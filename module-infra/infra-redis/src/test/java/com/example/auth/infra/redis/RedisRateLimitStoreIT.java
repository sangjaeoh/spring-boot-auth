package com.example.auth.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 레이트리밋 스토어 IT: 고정 창 카운터(첫 증가 시 TTL)·쿨다운 원자 획득을 실 Redis(Testcontainers)로
 * 검증한다.
 */
@SpringBootTest
@Testcontainers
class RedisRateLimitStoreIT {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisRateLimitStore store;

    @Autowired
    private StringRedisTemplate template;

    @Test
    void incrementCountsAndSetsWindowExpiryOnFirstIncrement() {
        String key = "test:" + UUID.randomUUID();

        assertThat(store.increment(key, Duration.ofMinutes(5))).isEqualTo(1);
        assertThat(store.increment(key, Duration.ofMinutes(5))).isEqualTo(2);
        assertThat(store.increment(key, Duration.ofMinutes(5))).isEqualTo(3);

        Long ttl = template.getExpire("rl:" + key);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5).toSeconds());
    }

    @Test
    void tryAcquireGrantsOnceUntilCooldownExpires() {
        String key = "cooldown:" + UUID.randomUUID();

        assertThat(store.tryAcquire(key, Duration.ofMinutes(1))).isTrue();
        assertThat(store.tryAcquire(key, Duration.ofMinutes(1))).isFalse();

        Long ttl = template.getExpire("rl:" + key);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(60);
    }
}
