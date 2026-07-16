package com.example.auth.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.crypto.PasswordHasher;
import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.HashAlgorithm;
import com.example.auth.domain.auth.entity.PasswordCredential;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.PasswordCredentialRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 비밀번호 이력 영속 IT: 변경 시 이력 적재·최근 N 축출(cascade·orphanRemoval)·재사용 금지를 실 PostgreSQL로 검증한다.
 */
@SpringBootTest
@Import({JpaConfig.class, PasswordHistoryIT.TestBeans.class})
@Testcontainers
class PasswordHistoryIT {

    private static final int HISTORY_LIMIT = 3;

    // KDF 호출 시점의 트랜잭션 활성 여부 기록 — 변경·재설정의 KDF가 커넥션 점유 없이(tx 밖) 돌았음을 판별.
    private static final List<Boolean> KDF_TX_ACTIVITY = new CopyOnWriteArrayList<>();

    // 결정적·고속 대조용 fake KDF(실 Argon2 경로는 app-api E2E가 커버). "enc:" 접두 인코딩.
    private static final PasswordHasher FAKE_HASHER = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            KDF_TX_ACTIVITY.add(TransactionSynchronizationManager.isActualTransactionActive());
            return "enc:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedHash) {
            KDF_TX_ACTIVITY.add(TransactionSynchronizationManager.isActualTransactionActive());
            return encodedHash.equals("enc:" + rawPassword);
        }

        @Override
        public String algorithm() {
            return "ARGON2ID";
        }
    };

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private PasswordCredentialRepository repository;

    @Autowired
    private PasswordCredentialModifier modifier;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void prunesHistoryToLimitAndBansRecentReuseOnly() {
        UUID userId = UUID.randomUUID();
        Instant t0 = Instant.now();
        repository.save(PasswordCredential.create(userId, FAKE_HASHER.hash("password0"), HashAlgorithm.ARGON2ID, t0));

        // 순차 변경 4회(각 시각 구분) → 한도 3이라 최신 3개(password2·3·4)만 남는다.
        modifier.change(userId, "password0", "password1", t0.plus(Duration.ofSeconds(1)));
        modifier.change(userId, "password1", "password2", t0.plus(Duration.ofSeconds(2)));
        modifier.change(userId, "password2", "password3", t0.plus(Duration.ofSeconds(3)));
        modifier.change(userId, "password3", "password4", t0.plus(Duration.ofSeconds(4)));

        Integer count = jdbc.queryForObject(
                "select count(*) from auth.password_history where user_id = ?", Integer.class, userId);
        assertThat(count).isEqualTo(HISTORY_LIMIT);

        // 최근 3개(password2·3·4) 재사용은 거부.
        assertThatThrownBy(() -> modifier.change(userId, "password4", "password2", t0.plus(Duration.ofSeconds(5))))
                .isInstanceOf(AuthException.class);

        // 축출된 옛 비번(password0·1)은 재사용 허용.
        modifier.change(userId, "password4", "password0", t0.plus(Duration.ofSeconds(6)));
    }

    @Test
    void runsKdfOutsideTransactionWhileChangeAndResetStillApplyAtomically() {
        UUID userId = UUID.randomUUID();
        Instant t0 = Instant.now();
        repository.save(PasswordCredential.create(userId, FAKE_HASHER.hash("password0"), HashAlgorithm.ARGON2ID, t0));
        KDF_TX_ACTIVITY.clear();

        modifier.change(userId, "password0", "password1", t0.plus(Duration.ofSeconds(1)));
        modifier.resetTo(userId, "password2", t0.plus(Duration.ofSeconds(2)));

        // KDF(현재 대조·재사용 대조·신규 인코딩)가 전부 트랜잭션 밖에서 실행됐다(커넥션 비점유).
        assertThat(KDF_TX_ACTIVITY).isNotEmpty().containsOnly(false);

        // 변경·재설정 결과는 정상 커밋됐다(재설정 후 해시 반영 + 직전 비번 재사용 거부).
        assertThat(repository.findById(userId).orElseThrow().getPasswordHash()).isEqualTo(FAKE_HASHER.hash("password2"));
        assertThatThrownBy(() -> modifier.resetTo(userId, "password1", t0.plus(Duration.ofSeconds(3))))
                .isInstanceOf(AuthException.class);
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        PasswordCredentialModifier passwordCredentialModifier(
                PasswordCredentialRepository repository, PlatformTransactionManager transactionManager) {
            MessagePublisher noopPublisher = event -> {};
            return new PasswordCredentialModifier(
                    repository,
                    FAKE_HASHER,
                    new PasswordPolicyValidator(8),
                    noopPublisher,
                    transactionManager,
                    HISTORY_LIMIT);
        }
    }
}
