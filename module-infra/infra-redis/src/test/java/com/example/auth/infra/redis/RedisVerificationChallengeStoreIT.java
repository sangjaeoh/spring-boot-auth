package com.example.auth.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.domain.auth.port.VerificationResult;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 인증코드 스토어 IT: 발급·원자검증(성공 소진·오답·시도상한)을 실 Redis(Testcontainers)로 검증한다.
 */
@SpringBootTest
@Testcontainers
class RedisVerificationChallengeStoreIT {

    private static final Duration TTL = Duration.ofMinutes(5);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisVerificationChallengeStore store;

    @Test
    void verifiesCorrectCodeOnceAndConsumesIt() {
        String challengeId = UUID.randomUUID().toString();
        UUID subjectId = UUID.randomUUID();
        store.issue(challengeId, subjectId, "codehash", TTL, 5);

        VerificationResult first = store.verify(challengeId, "codehash");
        assertThat(first.status()).isEqualTo(VerificationResult.Status.VERIFIED);
        assertThat(first.subjectId()).isEqualTo(subjectId);

        // single-use: 소진 후 재검증은 NOT_FOUND.
        assertThat(store.verify(challengeId, "codehash").status()).isEqualTo(VerificationResult.Status.NOT_FOUND);
    }

    @Test
    void rejectsWrongCode() {
        String challengeId = UUID.randomUUID().toString();
        store.issue(challengeId, UUID.randomUUID(), "codehash", TTL, 5);

        assertThat(store.verify(challengeId, "wrong").status()).isEqualTo(VerificationResult.Status.MISMATCH);
    }

    @Test
    void locksOutAfterMaxAttempts() {
        String challengeId = UUID.randomUUID().toString();
        store.issue(challengeId, UUID.randomUUID(), "codehash", TTL, 2);

        assertThat(store.verify(challengeId, "wrong").status()).isEqualTo(VerificationResult.Status.MISMATCH);
        assertThat(store.verify(challengeId, "wrong").status()).isEqualTo(VerificationResult.Status.MISMATCH);
        // 상한 도달 후엔 정답이라도 잠금(TOO_MANY_ATTEMPTS).
        assertThat(store.verify(challengeId, "codehash").status())
                .isEqualTo(VerificationResult.Status.TOO_MANY_ATTEMPTS);
    }

    @Test
    void verifyRejectsUnknownChallenge() {
        assertThat(store.verify(UUID.randomUUID().toString(), "codehash").status())
                .isEqualTo(VerificationResult.Status.NOT_FOUND);
    }
}
