package com.example.auth.infra.redis;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.LoginFailureCounter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 연속 로그인 실패 카운터다(고정 창 카운터+TTL — 레이트리밋과 같은 증가 스크립트 재사용).
 *
 * <p>저장소 예외는 fail-closed로 503을 던진다(세션 스토어와 동일 정책·동일 코드).
 */
@Component
public class RedisLoginFailureCounter implements LoginFailureCounter {

    private static final Logger log = LoggerFactory.getLogger(RedisLoginFailureCounter.class);
    private static final String PREFIX = "lockfail:";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> incrementScript;

    public RedisLoginFailureCounter(
            StringRedisTemplate redis, @Qualifier("rateLimitIncrementScript") RedisScript<Long> incrementScript) {
        this.redis = redis;
        this.incrementScript = incrementScript;
    }

    @Override
    public long increment(UUID userId, Duration window) {
        try {
            Long count = redis.execute(incrementScript, List.of(PREFIX + userId), Long.toString(window.toMillis()));
            if (count == null) {
                throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
            }
            return count;
        } catch (DataAccessException e) {
            log.warn("실패 카운터 증가 중 Redis 예외 — fail-closed", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    @Override
    public void reset(UUID userId) {
        try {
            redis.delete(PREFIX + userId);
        } catch (DataAccessException e) {
            log.warn("실패 카운터 리셋 중 Redis 예외 — fail-closed", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }
}
