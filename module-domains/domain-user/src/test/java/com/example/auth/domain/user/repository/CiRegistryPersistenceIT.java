package com.example.auth.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.crypto.EnvelopeCipher;
import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.user.entity.CiRegistry;
import com.example.auth.domain.user.entity.CiStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * CI 원장 영속 IT: link 저장/조회와 {@code ci_hash} 유니크 인덱스의 hard-enforce를 실 PostgreSQL로
 * 검증한다(다음 슬라이스 CreateUser 트랜잭션의 backstop이 지금부터 서 있음을 증명).
 */
@SpringBootTest
@Import({JpaConfig.class, CiRegistryPersistenceIT.TestBeans.class})
@Testcontainers
class CiRegistryPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private CiRegistryRepository repository;

    @Test
    void linksAndFindsByCiHash() {
        UUID userId = UUID.randomUUID();
        repository.save(CiRegistry.link("1:ci-abc", userId, Instant.now()));

        CiRegistry found = repository.findByCiHash("1:ci-abc").orElseThrow();
        assertThat(found.getStatus()).isEqualTo(CiStatus.ACTIVE_LINKED);
        assertThat(found.getLinkedUserId()).isEqualTo(userId);
        assertThat(found.getWithdrawnAt()).isNull();
    }

    @Test
    void enforcesGlobalCiHashUniqueness() {
        repository.save(CiRegistry.link("1:ci-dup", UUID.randomUUID(), Instant.now()));

        assertThatThrownBy(() -> repository.saveAndFlush(CiRegistry.link("1:ci-dup", UUID.randomUUID(), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * PII 컨버터가 있는 엔티티들이 함께 스캔되므로 {@code EnvelopeCipher} 빈만 fake로 둔다.
     */
    @TestConfiguration
    static class TestBeans {

        @Bean
        EnvelopeCipher envelopeCipher() {
            return new EnvelopeCipher() {
                @Override
                public String encrypt(String plaintext) {
                    return "enc1:" + Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String decrypt(String ciphertext) {
                    return new String(
                            Base64.getDecoder().decode(ciphertext.substring("enc1:".length())), StandardCharsets.UTF_8);
                }
            };
        }
    }
}
