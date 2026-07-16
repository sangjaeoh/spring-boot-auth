package com.example.auth.infra.redis;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.VerificationChallengeStore;
import com.example.auth.domain.auth.port.VerificationResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 인증코드 스토어다(TTL). 검증은 Lua로 원자 판정한다.
 *
 * <p>존재·시도상한·코드대조·시도증가·소진을 한 번의 Lua로 수행해 저엔트로피 코드의 시도상한 우회(병렬 추측)를
 * 막는다. 저장소 예외는 검증은 fail-closed(NOT_FOUND)로 처리해 재설정이 진행되지 않게 하고, 발급은 다른 쓰기
 * 경로와 같이 {@link AuthErrorCode#SESSION_STORE_UNAVAILABLE}(503)로 실패시켜 장애를 정직하게 알린다.
 */
@Component
public class RedisVerificationChallengeStore implements VerificationChallengeStore {

    private static final Logger log = LoggerFactory.getLogger(RedisVerificationChallengeStore.class);
    private static final String VERIFIED_PREFIX = "VERIFIED\n";

    private final StringRedisTemplate redis;
    private final RedisScript<String> verifyScript;

    public RedisVerificationChallengeStore(
            StringRedisTemplate redis, @Qualifier("verifyChallengeScript") RedisScript<String> verifyChallengeScript) {
        this.redis = redis;
        this.verifyScript = verifyChallengeScript;
    }

    @Override
    public void issue(String challengeId, UUID subjectId, String codeHash, Duration ttl, int maxAttempts) {
        String key = challengeKey(challengeId);
        Map<String, String> fields = Map.of(
                "subjectId",
                subjectId.toString(),
                "codeHash",
                codeHash,
                "attempts",
                "0",
                "maxAttempts",
                Integer.toString(maxAttempts));
        try {
            redis.<String, String>opsForHash().putAll(key, fields);
            redis.expire(key, ttl);
        } catch (DataAccessException e) {
            log.warn("인증코드 발급 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    @Override
    public VerificationResult verify(String challengeId, String codeHash) {
        try {
            String result = redis.execute(verifyScript, List.of(challengeKey(challengeId)), codeHash);
            if (result == null || result.equals("NOT_FOUND")) {
                return VerificationResult.notFound();
            }
            if (result.equals("MISMATCH")) {
                return VerificationResult.mismatch();
            }
            if (result.equals("TOO_MANY")) {
                return VerificationResult.tooManyAttempts();
            }
            if (result.startsWith(VERIFIED_PREFIX)) {
                return VerificationResult.verified(UUID.fromString(result.substring(VERIFIED_PREFIX.length())));
            }
            return VerificationResult.notFound();
        } catch (DataAccessException e) {
            log.warn("인증코드 검증 중 Redis 예외 — fail-closed", e);
            return VerificationResult.notFound();
        }
    }

    private static String challengeKey(String challengeId) {
        return "vchal:" + challengeId;
    }
}
