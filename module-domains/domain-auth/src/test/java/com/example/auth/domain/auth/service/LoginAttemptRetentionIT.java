package com.example.auth.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.auth.entity.LoginAttempt;
import com.example.auth.domain.auth.entity.LoginResult;
import com.example.auth.domain.auth.repository.LoginAttemptRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 로그인 이력 보존 IT: IP 90일 후 가명화(레코드·순서 유지, 멱등)와 2년 보존창 파기를 검증한다.
 */
@SpringBootTest
@Import({JpaConfig.class, LoginAttemptRetentionIT.TestBeans.class})
@Testcontainers
class LoginAttemptRetentionIT {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private LoginAttemptRemover remover;

    @Autowired
    private LoginAttemptRepository repository;

    @Test
    void anonymizesIpAfterWindowKeepingRecordAndOrder() {
        LoginAttempt staleV4 = attempt("203.0.113.7", NOW.minusSeconds(91L * 86_400));
        LoginAttempt staleV6 = attempt("2001:db8::1", NOW.minusSeconds(91L * 86_400));
        LoginAttempt fresh = attempt("198.51.100.9", NOW.minusSeconds(10L * 86_400));

        int anonymized = remover.anonymizeStaleIps(NOW);

        // 다른 테스트가 남긴 창 경과 행이 함께 처리될 수 있어 하한만 고정한다.
        assertThat(anonymized).isGreaterThanOrEqualTo(2);
        assertThat(repository.findById(staleV4.getId()).orElseThrow().getIp()).isEqualTo("203.0.*.*");
        assertThat(repository.findById(staleV6.getId()).orElseThrow().getIp()).isEqualTo("2001:*");
        assertThat(repository.findById(fresh.getId()).orElseThrow().getIp()).isEqualTo("198.51.100.9");

        // 멱등 — 재실행은 추가 변경이 없다(레코드 수·값 불변).
        assertThat(remover.anonymizeStaleIps(NOW)).isZero();
    }

    @Test
    void purgesAttemptsPastRetentionWindowOnly() {
        LoginAttempt expired = attempt("203.0.113.10", yearsAgo(3));
        LoginAttempt retained = attempt("203.0.113.11", yearsAgo(1));

        remover.purgeExpired(NOW);

        assertThat(repository.findById(expired.getId())).isEmpty();
        assertThat(repository.findById(retained.getId())).isPresent();
    }

    private LoginAttempt attempt(String ip, Instant at) {
        return repository.save(
                LoginAttempt.create(UUID.randomUUID(), LoginResult.SUCCESS, null, ip, null, 0, "KR", at));
    }

    private static Instant yearsAgo(int years) {
        return ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC).minusYears(years).toInstant();
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        LoginAttemptRemover loginAttemptRemover(LoginAttemptRepository repository) {
            return new LoginAttemptRemover(repository, 90, 2);
        }
    }
}
