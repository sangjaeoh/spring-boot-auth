package com.example.auth.infra.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 레이트리밋 스토어의 스크립트 빈을 조립한다.
 */
@Configuration
public class RedisRateLimitConfig {

    /**
     * 고정 창 카운터 증가(첫 증가 시 만료 세팅) Lua 스크립트를 로드한다.
     */
    @Bean
    public RedisScript<Long> rateLimitIncrementScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/rate_limit_increment.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
