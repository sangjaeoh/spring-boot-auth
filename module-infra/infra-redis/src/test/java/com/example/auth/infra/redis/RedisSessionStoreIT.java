package com.example.auth.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.domain.auth.port.RotationResult;
import java.time.Duration;
import java.time.Instant;
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
 * Redis 세션 스토어 IT: 생성·O(1)검증·무효화·회전(정상/유예/재사용)을 실 Redis(Testcontainers)로 검증한다.
 */
@SpringBootTest
@Testcontainers
class RedisSessionStoreIT {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Duration GRACE = Duration.ofSeconds(10);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisSessionStore store;

    @Test
    void createsValidatesAndRevokes() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        store.create(userId, sessionId, null, "1.2.3.4", "agent", "jti-1", "plain-1", now, TTL);

        assertThat(store.validate(userId, sessionId, now)).isTrue();

        store.revoke(userId, sessionId);
        assertThat(store.validate(userId, sessionId, now)).isFalse();
    }

    @Test
    void validateRejectsUnknownSession() {
        assertThat(store.validate(UUID.randomUUID(), UUID.randomUUID(), Instant.now()))
                .isFalse();
    }

    @Test
    void rotatesCurrentRefresh() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        store.create(userId, sessionId, null, "1.2.3.4", null, "jti-A", "plain-A", now, TTL);

        RotationResult result = store.rotate("jti-A", "jti-B", "plain-B", now, GRACE, TTL);

        assertThat(result.type()).isEqualTo(RotationResult.RotationType.ROTATED);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.sessionId()).isEqualTo(sessionId);
        assertThat(result.refreshPlain()).isEqualTo("plain-B");
        assertThat(store.validate(userId, sessionId, now)).isTrue();
    }

    @Test
    void replaysWithinGraceWindowIdempotently() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        store.create(userId, sessionId, null, "1.2.3.4", null, "jti-A", "plain-A", now, TTL);
        store.rotate("jti-A", "jti-B", "plain-B", now, GRACE, TTL);

        // 직전 토큰(jti-A)을 유예 창 안에서 재제시 → 회전이 발급한 동일 토큰(plain-B) 멱등 반환.
        RotationResult replay = store.rotate("jti-A", "jti-C", "plain-C", now, GRACE, TTL);

        assertThat(replay.type()).isEqualTo(RotationResult.RotationType.GRACE_REPLAY);
        assertThat(replay.refreshPlain()).isEqualTo("plain-B");
    }

    @Test
    void detectsReuseOutsideGraceAndRevokesFamily() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        store.create(userId, sessionId, null, "1.2.3.4", null, "jti-X", "plain-X", now, TTL);
        store.rotate("jti-X", "jti-Y", "plain-Y", now, GRACE, TTL);

        // 폐기된 옛 토큰(jti-X)을 유예 밖(grace=0, 1초 경과)에서 재사용 → 탈취 간주 → 패밀리 전멸.
        RotationResult reuse = store.rotate("jti-X", "jti-Z", "plain-Z", now.plusSeconds(1), Duration.ZERO, TTL);

        assertThat(reuse.type()).isEqualTo(RotationResult.RotationType.REUSE);
        assertThat(reuse.userId()).isEqualTo(userId);
        assertThat(store.validate(userId, sessionId, now)).isFalse();
    }

    @Test
    void rotationSlidesExpiryConsistently() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant t0 = Instant.now();
        store.create(userId, sessionId, null, "1.2.3.4", null, "jti-A", "plain-A", t0, TTL); // expiresAt = t0+30m

        // 회전 전: 원래 만료(t0+30m) 이후엔 검증 실패.
        assertThat(store.validate(userId, sessionId, t0.plus(Duration.ofMinutes(31))))
                .isFalse();

        // t0+20m에 회전 → 만료가 t0+50m로 슬라이드(키 TTL·expiresAt·인덱스 정합).
        store.rotate("jti-A", "jti-B", "plain-B", t0.plus(Duration.ofMinutes(20)), GRACE, TTL);

        // 원래 만료 이후·슬라이드 만료 이전 시점은 통과, 슬라이드 만료 이후는 실패.
        assertThat(store.validate(userId, sessionId, t0.plus(Duration.ofMinutes(40))))
                .isTrue();
        assertThat(store.validate(userId, sessionId, t0.plus(Duration.ofMinutes(51))))
                .isFalse();

        // 슬라이드 후에도 인덱스가 살아 있어 revokeAll이 세션을 무효화한다.
        store.revokeAll(userId);
        assertThat(store.validate(userId, sessionId, t0.plus(Duration.ofMinutes(40))))
                .isFalse();
    }

    @Test
    void rotateRejectsUnknownToken() {
        assertThat(store.rotate("nope", "new", "plain", Instant.now(), GRACE, TTL)
                        .type())
                .isEqualTo(RotationResult.RotationType.INVALID);
    }
}
