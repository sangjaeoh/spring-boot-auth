package com.example.auth.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.RotationResult;
import com.example.auth.domain.auth.port.SessionSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Redis 세션 스토어 IT: 생성(동시 상한·최오래 축출)·O(1)검증·목록·무효화(단건/전체/제외/기기)·회전
 * (정상/유예/재사용)·revoke 복제 확인(fail-closed)을 실 Redis(Testcontainers)로 검증한다.
 */
@SpringBootTest
@Testcontainers
class RedisSessionStoreIT {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Duration GRACE = Duration.ofSeconds(10);
    private static final int MAX_SESSIONS = 3;

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisSessionStore store;

    @Autowired
    private StringRedisTemplate template;

    @Autowired
    @Qualifier("rotateSessionScript")
    private RedisScript<String> rotateScript;

    @Autowired
    @Qualifier("createSessionScript")
    private RedisScript<String> createScript;

    @Autowired
    @Qualifier("revokeAllSessionsScript")
    private RedisScript<Long> revokeAllScript;

    @Test
    void createsValidatesAndRevokes() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        create(userId, sessionId, UUID.randomUUID(), now, "jti-1", "plain-1");

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
    void evictsOldestWhenConcurrentLimitExceeded() {
        UUID userId = UUID.randomUUID();
        Instant base = Instant.now();
        UUID oldest = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();
        assertThat(create(userId, oldest, UUID.randomUUID(), base, "jti-1", "p1"))
                .isEmpty();
        assertThat(create(userId, second, UUID.randomUUID(), base.plusMillis(1), "jti-2", "p2"))
                .isEmpty();
        assertThat(create(userId, third, UUID.randomUUID(), base.plusMillis(2), "jti-3", "p3"))
                .isEmpty();

        // 4번째 생성 → 상한 3 초과분인 최오래(oldest) 세션이 원자 축출되고 그 ID가 반환된다.
        List<UUID> evicted = create(userId, fourth, UUID.randomUUID(), base.plusMillis(3), "jti-4", "p4");

        assertThat(evicted).containsExactly(oldest);
        Instant now = base.plusMillis(4);
        assertThat(store.validate(userId, oldest, now)).isFalse();
        assertThat(store.validate(userId, second, now)).isTrue();
        assertThat(store.validate(userId, third, now)).isTrue();
        assertThat(store.validate(userId, fourth, now)).isTrue();
    }

    @Test
    void expiredIndexResidueIsPrunedWithoutCountingTowardLimitOrEviction() {
        UUID userId = UUID.randomUUID();
        Instant base = Instant.now();
        UUID expired = UUID.randomUUID();
        create(userId, expired, UUID.randomUUID(), base, "jti-e", "pe");
        // 세션 해시만 TTL 만료된 상황을 재현한다(인덱스 멤버는 잔류).
        template.delete(SessionKeys.sessionKey(userId, expired));

        create(userId, UUID.randomUUID(), UUID.randomUUID(), base.plusMillis(1), "jti-a", "pa");
        create(userId, UUID.randomUUID(), UUID.randomUUID(), base.plusMillis(2), "jti-b", "pb");
        List<UUID> evicted = create(userId, UUID.randomUUID(), UUID.randomUUID(), base.plusMillis(3), "jti-c", "pc");

        // 잔여 인덱스가 상한 카운트에 끼지 않아 3번째 실 세션 생성은 축출 없이 성립한다.
        assertThat(evicted).isEmpty();
        assertThat(template.opsForZSet().range(SessionKeys.indexKey(userId), 0, -1))
                .hasSize(3)
                .doesNotContain(expired.toString());
    }

    @Test
    void concurrentCreatesNeverExceedLimit() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        int attempts = 12;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger evictedTotal = new AtomicInteger();
        List<UUID> sessionIds = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            sessionIds.add(UUID.randomUUID());
        }
        for (int i = 0; i < attempts; i++) {
            UUID sessionId = sessionIds.get(i);
            String jti = "jti-race-" + i;
            executor.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    evictedTotal.addAndGet(create(userId, sessionId, UUID.randomUUID(), Instant.now(), jti, jti)
                            .size());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 경합 하에서도 활성 세션은 상한을 넘지 않고, 활성 + 축출 = 시도 전체다(유실·초과 없음).
        List<SessionSnapshot> active = store.findAllActive(userId, Instant.now());
        assertThat(active).hasSize(MAX_SESSIONS);
        assertThat(evictedTotal.get()).isEqualTo(attempts - MAX_SESSIONS);
    }

    @Test
    void listsActiveSessionsWithDeviceBindingAndAccessTime() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.now();
        create(userId, sessionId, deviceId, now, "jti-list-A", "plain-A");

        List<SessionSnapshot> sessions = store.findAllActive(userId, now);

        assertThat(sessions).hasSize(1);
        SessionSnapshot snapshot = sessions.get(0);
        assertThat(snapshot.sessionId()).isEqualTo(sessionId);
        assertThat(snapshot.deviceId()).isEqualTo(deviceId);
        assertThat(snapshot.ip()).isEqualTo("1.2.3.4");
        assertThat(snapshot.userAgent()).isEqualTo("agent");
        assertThat(snapshot.issuedAt()).isEqualTo(Instant.ofEpochMilli(now.toEpochMilli()));
        assertThat(snapshot.lastAccessedAt()).isEqualTo(Instant.ofEpochMilli(now.toEpochMilli()));

        // 회전이 최근 접속 시각을 전진시킨다.
        Instant later = now.plusSeconds(60);
        store.rotate("jti-list-A", "jti-list-B", "plain-B", later, GRACE, TTL);
        assertThat(store.findAllActive(userId, later).get(0).lastAccessedAt())
                .isEqualTo(Instant.ofEpochMilli(later.toEpochMilli()));
    }

    @Test
    void revokesAllExceptCurrentSession() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID current = UUID.randomUUID();
        UUID other1 = UUID.randomUUID();
        UUID other2 = UUID.randomUUID();
        create(userId, other1, UUID.randomUUID(), now, "jti-1", "p1");
        create(userId, other2, UUID.randomUUID(), now.plusMillis(1), "jti-2", "p2");
        create(userId, current, UUID.randomUUID(), now.plusMillis(2), "jti-3", "p3");

        store.revokeAllExcept(userId, current);

        assertThat(store.validate(userId, current, now)).isTrue();
        assertThat(store.validate(userId, other1, now)).isFalse();
        assertThat(store.validate(userId, other2, now)).isFalse();
    }

    @Test
    void revokesAllSessionsAtomicallyIncludingIndex() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();
        create(userId, s1, UUID.randomUUID(), now, "jti-ra-1", "p1");
        create(userId, s2, UUID.randomUUID(), now.plusMillis(1), "jti-ra-2", "p2");
        create(userId, s3, UUID.randomUUID(), now.plusMillis(2), "jti-ra-3", "p3");

        store.revokeAll(userId);

        assertThat(store.validate(userId, s1, now)).isFalse();
        assertThat(store.validate(userId, s2, now)).isFalse();
        assertThat(store.validate(userId, s3, now)).isFalse();
        // 세션 해시·인덱스가 한 Lua 안에서 함께 삭제된다(잔여 키 없음).
        assertThat(template.hasKey(SessionKeys.indexKey(userId))).isFalse();
        assertThat(template.hasKey(SessionKeys.sessionKey(userId, s1))).isFalse();
    }

    @Test
    void revokesSessionsBoundToDevice() {
        UUID userId = UUID.randomUUID();
        UUID targetDevice = UUID.randomUUID();
        UUID otherDevice = UUID.randomUUID();
        Instant now = Instant.now();
        UUID onTarget = UUID.randomUUID();
        UUID onOther = UUID.randomUUID();
        create(userId, onTarget, targetDevice, now, "jti-1", "p1");
        create(userId, onOther, otherDevice, now.plusMillis(1), "jti-2", "p2");

        store.revokeByDevice(userId, targetDevice);

        assertThat(store.validate(userId, onTarget, now)).isFalse();
        assertThat(store.validate(userId, onOther, now)).isTrue();
    }

    @Test
    void revokeFailsClosedWhenReplicaAcknowledgementFallsShort() {
        // 복제본 없는 단일 노드에서 min-replicas=1 → WAIT 확인 미달 → 503(fail-closed). prod Cluster의
        // failover 유실 차단 배선을 표준 Redis 의미론으로 검증한다.
        RedisSessionStore durableStore =
                new RedisSessionStore(template, rotateScript, createScript, revokeAllScript, 1, 100);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        create(userId, sessionId, UUID.randomUUID(), Instant.now(), "jti-w", "pw");

        assertThatThrownBy(() -> durableStore.revoke(userId, sessionId))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        assertThatThrownBy(() -> durableStore.revokeAll(userId))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
    }

    @Test
    void rotatesCurrentRefresh() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        create(userId, sessionId, UUID.randomUUID(), now, "jti-A", "plain-A");

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
        create(userId, sessionId, UUID.randomUUID(), now, "jti-A", "plain-A");
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
        create(userId, sessionId, UUID.randomUUID(), now, "jti-X", "plain-X");
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
        create(userId, sessionId, UUID.randomUUID(), t0, "jti-A", "plain-A"); // expiresAt = t0+30m

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

    @Test
    void returnsRotatedEvenWhenRefIndexUpdateFailsAfterAtomicRotation() {
        // create의 refidx set은 통과시키고, 회전이 Lua로 확정된 뒤의 refidx set에만 Redis 예외를 주입한다
        // (닫힌 포트 테스트는 첫 홉에서 끝나 이 분기를 못 태운다). ValueOperations.set 두 번째 호출만 실패.
        StringRedisTemplate spyTemplate = Mockito.spy(template);
        ValueOperations<String, String> spyValueOps = Mockito.spy(template.opsForValue());
        Mockito.doReturn(spyValueOps).when(spyTemplate).opsForValue();
        Mockito.doCallRealMethod()
                .doThrow(new RedisSystemException("injected", new RuntimeException()))
                .when(spyValueOps)
                .set(anyString(), anyString(), any(Duration.class));
        RedisSessionStore failingRefIndexStore =
                new RedisSessionStore(spyTemplate, rotateScript, createScript, revokeAllScript, 0, 250);

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        failingRefIndexStore.create(
                userId, sessionId, UUID.randomUUID(), "1.2.3.4", null, "jti-ref-A", "plain-A", now, TTL, MAX_SESSIONS);

        RotationResult rotated = failingRefIndexStore.rotate("jti-ref-A", "jti-ref-B", "plain-B", now, GRACE, TTL);

        // 회전은 이미 원자 확정됐으므로 refidx set 실패가 503/오탐 REUSE로 오보되지 않는다.
        assertThat(rotated.type()).isEqualTo(RotationResult.RotationType.ROTATED);
        assertThat(rotated.refreshPlain()).isEqualTo("plain-B");
        assertThat(failingRefIndexStore.validate(userId, sessionId, now)).isTrue();

        // refidx:jti-ref-B 부재 → 다음 회전은 INVALID(귀결: 다음 회전 시 재로그인). fail-open 없음.
        assertThat(failingRefIndexStore
                        .rotate("jti-ref-B", "jti-ref-C", "plain-C", now, GRACE, TTL)
                        .type())
                .isEqualTo(RotationResult.RotationType.INVALID);
    }

    private List<UUID> create(UUID userId, UUID sessionId, UUID deviceId, Instant now, String jti, String plain) {
        return store.create(userId, sessionId, deviceId, "1.2.3.4", "agent", jti, plain, now, TTL, MAX_SESSIONS);
    }
}
