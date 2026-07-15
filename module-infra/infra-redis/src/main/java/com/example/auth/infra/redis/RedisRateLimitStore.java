package com.example.auth.infra.redis;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.RateLimitStore;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 레이트리밋 스토어다(고정 창 카운터+TTL, 쿨다운은 SET NX).
 *
 * <p>증가+만료 세팅은 Lua로 원자화한다(단일 키 — 슬롯 무관). 저장소 예외는 fail-closed로 503을
 * 던진다 — 장애가 레이트리밋 우회로가 되지 않게 한다(세션 스토어와 동일 정책·동일 코드).
 */
@Component
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);
    private static final String PREFIX = "rl:";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> incrementScript;

    public RedisRateLimitStore(
            StringRedisTemplate redis, @Qualifier("rateLimitIncrementScript") RedisScript<Long> incrementScript) {
        this.redis = redis;
        this.incrementScript = incrementScript;
    }

    @Override
    public long increment(String key, Duration window) {
        try {
            Long count = redis.execute(incrementScript, List.of(PREFIX + key), Long.toString(window.toMillis()));
            if (count == null) {
                throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
            }
            return count;
        } catch (DataAccessException e) {
            log.warn("레이트리밋 카운터 증가 중 Redis 예외 — fail-closed", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    @Override
    public boolean tryAcquire(String key, Duration cooldown) {
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(PREFIX + key, "1", cooldown);
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException e) {
            log.warn("쿨다운 획득 중 Redis 예외 — fail-closed", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }
}
