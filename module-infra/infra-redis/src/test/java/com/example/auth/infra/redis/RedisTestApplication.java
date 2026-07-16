package com.example.auth.infra.redis;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * infra-redis 통합 테스트용 부트 설정(Redis auto-config + 세션 스토어 빈).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
    RedisClientConfig.class,
    RedisSessionConfig.class,
    RedisSessionStore.class,
    RedisChallengeConfig.class,
    RedisVerificationChallengeStore.class,
    RedisRegistrationConfig.class,
    RedisRegistrationSessionStore.class,
    RedisRateLimitConfig.class,
    RedisRateLimitStore.class
})
class RedisTestApplication {}
