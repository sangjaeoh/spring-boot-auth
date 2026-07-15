package com.example.auth.infra.redis;

import static java.util.Objects.requireNonNull;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.RotationResult;
import com.example.auth.domain.auth.port.SessionSnapshot;
import com.example.auth.domain.auth.port.SessionStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 세션 스토어다(진실원본). 생성(동시 세션 상한 검증 + 최오래 축출)과 회전은 Lua로 원자
 * 판정한다.
 *
 * <p>키는 {@link SessionKeys}가 {@code {u:userId}} 해시태그로 단일 슬롯을 이뤄 Cluster에서도 Lua 원자성을
 * 보존한다. 저장소 예외 처리는 판정 지점별로 다르다 — 읽기 핫패스 {@code validate}는 fail-closed로 무효
 * 처리하고(가용성보다 실시간 무효화), 그 외 연산은 {@link AuthErrorCode#SESSION_STORE_UNAVAILABLE}(503)로
 * 실패시켜 토큰을 발급하지 않는다. revoke 계열은 {@code auth.session.revoke-durability.min-replicas} 설정
 * 시 {@code WAIT}로 복제 확인까지 강제해 failover 시 무효화 유실을 차단한다(미달 시 동일 503).
 */
@Component
public class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String GRACE_REPLAY_PREFIX = "GRACE_REPLAY\n";

    private final StringRedisTemplate redis;
    private final RedisScript<String> rotateScript;
    private final RedisScript<String> createScript;
    private final int revokeMinReplicas;
    private final Duration revokeReplicaTimeout;

    public RedisSessionStore(
            StringRedisTemplate redis,
            @Qualifier("rotateSessionScript") RedisScript<String> rotateSessionScript,
            @Qualifier("createSessionScript") RedisScript<String> createSessionScript,
            @Value("${auth.session.revoke-durability.min-replicas:0}") int revokeMinReplicas,
            @Value("${auth.session.revoke-durability.timeout-ms:250}") long revokeReplicaTimeoutMs) {
        this.redis = redis;
        this.rotateScript = rotateSessionScript;
        this.createScript = createSessionScript;
        this.revokeMinReplicas = revokeMinReplicas;
        this.revokeReplicaTimeout = Duration.ofMillis(revokeReplicaTimeoutMs);
    }

    @Override
    public List<UUID> create(
            UUID userId,
            UUID sessionId,
            UUID deviceId,
            String ip,
            @Nullable String userAgent,
            String refreshJtiHash,
            String refreshPlain,
            Instant now,
            Duration sessionTtl,
            int maxSessions) {
        @Nullable String evictedJoined;
        try {
            evictedJoined = redis.execute(
                    createScript,
                    List.of(SessionKeys.sessionKey(userId, sessionId), SessionKeys.indexKey(userId)),
                    sessionId.toString(),
                    deviceId.toString(),
                    ip,
                    userAgent == null ? "" : userAgent,
                    refreshJtiHash,
                    Long.toString(now.toEpochMilli()),
                    Long.toString(sessionTtl.toMillis()),
                    Integer.toString(maxSessions),
                    SessionKeys.sessionPrefix(userId));
            // 역인덱스는 회전 세대마다 누적되나 sessionTtl로 자동 만료된다(다세대 재사용 탐지를 위해 제시분을
            // 즉시 삭제하지 않는다). 규모 시 전용 짧은 TTL로 분리를 검토한다. refidx를 마지막에 세워 부분
            // 실패 시 세션이 resolve되지 않게 한다(fail-closed). 비원자 부분쓰기 잔류는 REDIS_HA.md 참조.
            redis.opsForValue().set(SessionKeys.refIndexKey(refreshJtiHash), userId + "|" + sessionId, sessionTtl);
        } catch (DataAccessException e) {
            log.warn("세션 생성 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
        if (evictedJoined == null || evictedJoined.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(evictedJoined.split(",")).map(UUID::fromString).toList();
    }

    @Override
    public boolean validate(UUID userId, UUID sessionId, Instant now) {
        try {
            HashOperations<String, String, String> hash = redis.opsForHash();
            List<String> values =
                    hash.multiGet(SessionKeys.sessionKey(userId, sessionId), List.of("status", "expiresAt"));
            String status = values.get(0);
            String expiresAt = values.get(1);
            if (status == null || expiresAt == null) {
                return false;
            }
            return STATUS_ACTIVE.equals(status) && now.toEpochMilli() <= Long.parseLong(expiresAt);
        } catch (DataAccessException e) {
            log.warn("세션 검증 중 Redis 예외 — fail-closed", e);
            return false;
        }
    }

    @Override
    public List<SessionSnapshot> findAllActive(UUID userId, Instant now) {
        try {
            Set<String> members = redis.opsForZSet().range(SessionKeys.indexKey(userId), 0, -1);
            if (members == null) {
                return List.of();
            }
            HashOperations<String, String, String> hash = redis.opsForHash();
            List<SessionSnapshot> snapshots = new ArrayList<>();
            for (String sessionId : members) {
                List<String> values = hash.multiGet(
                        SessionKeys.sessionPrefix(userId) + sessionId,
                        List.of("status", "deviceId", "ip", "ua", "issuedAt", "lastAccessedAt", "expiresAt"));
                String status = values.get(0);
                String expiresAt = values.get(6);
                // TTL 만료로 해시만 사라진 잔여 인덱스 멤버는 건너뛴다(다음 생성 Lua가 걷어낸다).
                if (status == null || expiresAt == null) {
                    continue;
                }
                if (!STATUS_ACTIVE.equals(status) || now.toEpochMilli() > Long.parseLong(expiresAt)) {
                    continue;
                }
                String userAgent = requireNonNull(values.get(3));
                snapshots.add(new SessionSnapshot(
                        UUID.fromString(sessionId),
                        UUID.fromString(requireNonNull(values.get(1))),
                        requireNonNull(values.get(2)),
                        userAgent.isEmpty() ? null : userAgent,
                        Instant.ofEpochMilli(Long.parseLong(requireNonNull(values.get(4)))),
                        Instant.ofEpochMilli(Long.parseLong(requireNonNull(values.get(5))))));
            }
            return snapshots;
        } catch (DataAccessException e) {
            log.warn("세션 목록 조회 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    @Override
    public RotationResult rotate(
            String presentedJtiHash,
            String newJtiHash,
            String newRefreshPlain,
            Instant now,
            Duration graceWindow,
            Duration sessionTtl) {
        // GET·Lua 원자 판정만 fail-closed(503)로 감싼다. Lua가 ROTATED를 확정한 뒤의 refidx SET을 여기
        // 넣으면 완료된 회전이 503/오탐 REUSE로 오보되므로 아래 best-effort로 분리한다(귀결: REDIS_HA.md).
        @Nullable String mapping;
        try {
            mapping = redis.opsForValue().get(SessionKeys.refIndexKey(presentedJtiHash));
        } catch (DataAccessException e) {
            log.warn("세션 회전(refidx 조회) 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
        if (mapping == null) {
            return RotationResult.invalid();
        }
        int sep = mapping.indexOf('|');
        UUID userId = UUID.fromString(mapping.substring(0, sep));
        UUID sessionId = UUID.fromString(mapping.substring(sep + 1));

        @Nullable String result;
        try {
            result = redis.execute(
                    rotateScript,
                    List.of(SessionKeys.sessionKey(userId, sessionId), SessionKeys.indexKey(userId)),
                    presentedJtiHash,
                    newJtiHash,
                    newRefreshPlain,
                    Long.toString(now.toEpochMilli()),
                    Long.toString(graceWindow.toMillis()),
                    Long.toString(sessionTtl.toMillis()),
                    SessionKeys.gracePrefix(userId),
                    SessionKeys.sessionPrefix(userId));
        } catch (DataAccessException e) {
            log.warn("세션 회전(Lua) 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }

        if (result == null || result.equals("INVALID")) {
            return RotationResult.invalid();
        }
        if (result.equals("ROTATED")) {
            try {
                redis.opsForValue().set(SessionKeys.refIndexKey(newJtiHash), userId + "|" + sessionId, sessionTtl);
            } catch (DataAccessException e) {
                // 회전은 Lua로 이미 원자 확정됨 — refidx는 best-effort(실패 시 다음 회전에서 재로그인).
                log.warn("회전 확정 후 refidx 갱신 실패 — 회전은 유효, 다음 회전 시 재로그인 감수", e);
            }
            return RotationResult.rotated(userId, sessionId, newRefreshPlain);
        }
        if (result.startsWith(GRACE_REPLAY_PREFIX)) {
            return RotationResult.graceReplay(userId, sessionId, result.substring(GRACE_REPLAY_PREFIX.length()));
        }
        return RotationResult.reuse(userId, sessionId);
    }

    @Override
    public void revoke(UUID userId, UUID sessionId) {
        try {
            redis.delete(SessionKeys.sessionKey(userId, sessionId));
            redis.opsForZSet().remove(SessionKeys.indexKey(userId), sessionId.toString());
            awaitRevokeDurability();
        } catch (DataAccessException e) {
            log.warn("세션 무효화 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    @Override
    public void revokeAll(UUID userId) {
        try {
            Set<String> members = redis.opsForZSet().range(SessionKeys.indexKey(userId), 0, -1);
            if (members != null) {
                for (String sessionId : members) {
                    redis.delete(SessionKeys.sessionPrefix(userId) + sessionId);
                }
            }
            redis.delete(SessionKeys.indexKey(userId));
            awaitRevokeDurability();
        } catch (DataAccessException e) {
            log.warn("세션 전체 무효화 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    @Override
    public void revokeAllExcept(UUID userId, UUID keepSessionId) {
        String keep = keepSessionId.toString();
        try {
            Set<String> members = redis.opsForZSet().range(SessionKeys.indexKey(userId), 0, -1);
            if (members != null) {
                for (String sessionId : members) {
                    if (keep.equals(sessionId)) {
                        continue;
                    }
                    redis.delete(SessionKeys.sessionPrefix(userId) + sessionId);
                    redis.opsForZSet().remove(SessionKeys.indexKey(userId), sessionId);
                }
            }
            awaitRevokeDurability();
        } catch (DataAccessException e) {
            log.warn("세션 선택 무효화 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    @Override
    public void revokeByDevice(UUID userId, UUID deviceId) {
        String device = deviceId.toString();
        try {
            Set<String> members = redis.opsForZSet().range(SessionKeys.indexKey(userId), 0, -1);
            if (members != null) {
                HashOperations<String, String, String> hash = redis.opsForHash();
                for (String sessionId : members) {
                    String sessKey = SessionKeys.sessionPrefix(userId) + sessionId;
                    if (device.equals(hash.get(sessKey, "deviceId"))) {
                        redis.delete(sessKey);
                        redis.opsForZSet().remove(SessionKeys.indexKey(userId), sessionId);
                    }
                }
            }
            awaitRevokeDurability();
        } catch (DataAccessException e) {
            log.warn("기기 세션 무효화 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    /**
     * revoke 쓰기가 최소 {@code min-replicas}개 복제본에 도달했는지 {@code WAIT}로 확인한다. 미설정(0)이면
     * 건너뛰고, 시한 내 미달이면 503으로 실패시켜 failover 시 무효화가 조용히 유실되지 않게 한다.
     */
    private void awaitRevokeDurability() {
        if (revokeMinReplicas <= 0) {
            return;
        }
        Long acked = redis.execute((RedisCallback<Long>) connection -> (Long) connection.execute(
                "WAIT",
                Integer.toString(revokeMinReplicas).getBytes(StandardCharsets.UTF_8),
                Long.toString(revokeReplicaTimeout.toMillis()).getBytes(StandardCharsets.UTF_8)));
        if (acked == null || acked < revokeMinReplicas) {
            log.warn("revoke 복제 확인 미달(acked={}, 요구={}) — fail-closed(503)", acked, revokeMinReplicas);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }
}
