package com.example.auth.infra.redis;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.RotationResult;
import com.example.auth.domain.auth.port.SessionStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 세션 스토어다(진실원본). 회전은 Lua로 원자 판정한다.
 *
 * <p>키는 {@link SessionKeys}가 {@code {u:userId}} 해시태그로 단일 슬롯을 이뤄 Cluster에서도 Lua 원자성을
 * 보존한다. 저장소 예외 처리는 판정 지점별로 다르다 — 읽기 핫패스 {@code validate}는 fail-closed로 무효
 * 처리하고(가용성보다 실시간 무효화), 쓰기·회전은 {@link AuthErrorCode#SESSION_STORE_UNAVAILABLE}(503)로
 * 실패시켜 토큰을 발급하지 않는다. 동시세션 상한·축출은 이 슬라이스 범위 밖(Phase 3)이라 create는 세션
 * 인덱스만 유지한다.
 */
@Component
public class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String GRACE_REPLAY_PREFIX = "GRACE_REPLAY\n";

    private final StringRedisTemplate redis;
    private final RedisScript<String> rotateScript;

    public RedisSessionStore(
            StringRedisTemplate redis, @Qualifier("rotateSessionScript") RedisScript<String> rotateSessionScript) {
        this.redis = redis;
        this.rotateScript = rotateSessionScript;
    }

    @Override
    public void create(
            UUID userId,
            UUID sessionId,
            @Nullable UUID deviceId,
            String ip,
            @Nullable String userAgent,
            String refreshJtiHash,
            String refreshPlain,
            Instant now,
            Duration sessionTtl) {
        long nowMs = now.toEpochMilli();
        long expiresMs = nowMs + sessionTtl.toMillis();
        Map<String, String> fields = Map.of(
                "status",
                STATUS_ACTIVE,
                "deviceId",
                deviceId == null ? "" : deviceId.toString(),
                "ip",
                ip,
                "ua",
                userAgent == null ? "" : userAgent,
                "issuedAt",
                Long.toString(nowMs),
                "expiresAt",
                Long.toString(expiresMs),
                "refreshJtiHash",
                refreshJtiHash,
                "prevJtiHash",
                "",
                "prevRotatedAt",
                "0");

        String sessKey = SessionKeys.sessionKey(userId, sessionId);
        try {
            redis.<String, String>opsForHash().putAll(sessKey, fields);
            redis.expire(sessKey, sessionTtl);
            redis.opsForZSet().add(SessionKeys.indexKey(userId), sessionId.toString(), nowMs);
            redis.expire(SessionKeys.indexKey(userId), sessionTtl);
            // 역인덱스는 회전 세대마다 누적되나 sessionTtl로 자동 만료된다(다세대 재사용 탐지를 위해 제시분을
            // 즉시 삭제하지 않는다). 규모 시 전용 짧은 TTL로 분리를 검토한다. refidx를 마지막에 세워 부분
            // 실패 시 세션이 resolve되지 않게 한다(fail-closed). 비원자 부분쓰기 잔류는 REDIS_HA.md 참조.
            redis.opsForValue().set(SessionKeys.refIndexKey(refreshJtiHash), userId + "|" + sessionId, sessionTtl);
        } catch (DataAccessException e) {
            log.warn("세션 생성 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
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
        } catch (DataAccessException e) {
            log.warn("세션 전체 무효화 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }
}
