package com.example.auth.infra.redis;

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
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 세션 스토어다(진실원본). 회전은 Lua로 원자 판정한다.
 *
 * <p>키는 {@code {u:userId}} 해시태그로 단일 슬롯을 이뤄 Cluster에서도 Lua 원자성을 보존한다. 저장소
 * 예외는 fail-closed(검증 실패·무효)로 처리해 실시간 무효화의 안전측을 지킨다. 동시세션 상한·축출은 이
 * 슬라이스 범위 밖(Phase 3)이라 create는 세션 인덱스만 유지한다.
 */
@Component
public class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String GRACE_REPLAY_PREFIX = "GRACE_REPLAY\n";

    private final StringRedisTemplate redis;
    private final RedisScript<String> rotateScript;

    public RedisSessionStore(StringRedisTemplate redis, RedisScript<String> rotateSessionScript) {
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

        String sessKey = sessionKey(userId, sessionId);
        redis.<String, String>opsForHash().putAll(sessKey, fields);
        redis.expire(sessKey, sessionTtl);
        redis.opsForZSet().add(indexKey(userId), sessionId.toString(), nowMs);
        redis.expire(indexKey(userId), sessionTtl);
        // 역인덱스는 회전 세대마다 누적되나 sessionTtl로 자동 만료된다(다세대 재사용 탐지를 위해 제시분을
        // 즉시 삭제하지 않는다). 규모 시 전용 짧은 TTL로 분리를 검토한다.
        redis.opsForValue().set(refIndexKey(refreshJtiHash), userId + "|" + sessionId, sessionTtl);
    }

    @Override
    public boolean validate(UUID userId, UUID sessionId, Instant now) {
        try {
            HashOperations<String, String, String> hash = redis.opsForHash();
            List<String> values = hash.multiGet(sessionKey(userId, sessionId), List.of("status", "expiresAt"));
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
        String mapping = redis.opsForValue().get(refIndexKey(presentedJtiHash));
        if (mapping == null) {
            return RotationResult.invalid();
        }
        int sep = mapping.indexOf('|');
        UUID userId = UUID.fromString(mapping.substring(0, sep));
        UUID sessionId = UUID.fromString(mapping.substring(sep + 1));

        String result = redis.execute(
                rotateScript,
                List.of(sessionKey(userId, sessionId), indexKey(userId)),
                presentedJtiHash,
                newJtiHash,
                newRefreshPlain,
                Long.toString(now.toEpochMilli()),
                Long.toString(graceWindow.toMillis()),
                Long.toString(sessionTtl.toMillis()),
                gracePrefix(userId),
                sessionPrefix(userId));

        if (result == null || result.equals("INVALID")) {
            return RotationResult.invalid();
        }
        if (result.equals("ROTATED")) {
            redis.opsForValue().set(refIndexKey(newJtiHash), userId + "|" + sessionId, sessionTtl);
            return RotationResult.rotated(userId, sessionId, newRefreshPlain);
        }
        if (result.startsWith(GRACE_REPLAY_PREFIX)) {
            return RotationResult.graceReplay(userId, sessionId, result.substring(GRACE_REPLAY_PREFIX.length()));
        }
        return RotationResult.reuse(userId, sessionId);
    }

    @Override
    public void revoke(UUID userId, UUID sessionId) {
        redis.delete(sessionKey(userId, sessionId));
        redis.opsForZSet().remove(indexKey(userId), sessionId.toString());
    }

    @Override
    public void revokeAll(UUID userId) {
        Set<String> members = redis.opsForZSet().range(indexKey(userId), 0, -1);
        if (members != null) {
            for (String sessionId : members) {
                redis.delete(sessionPrefix(userId) + sessionId);
            }
        }
        redis.delete(indexKey(userId));
    }

    private static String tag(UUID userId) {
        return "{u:" + userId + "}";
    }

    private static String sessionPrefix(UUID userId) {
        return "sess:" + tag(userId) + ":";
    }

    private static String sessionKey(UUID userId, UUID sessionId) {
        return sessionPrefix(userId) + sessionId;
    }

    private static String indexKey(UUID userId) {
        return "sessidx:" + tag(userId);
    }

    private static String gracePrefix(UUID userId) {
        return "grace:" + tag(userId) + ":";
    }

    private static String refIndexKey(String jtiHash) {
        return "refidx:" + jtiHash;
    }
}
