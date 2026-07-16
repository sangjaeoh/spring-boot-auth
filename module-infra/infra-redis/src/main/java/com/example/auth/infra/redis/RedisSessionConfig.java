package com.example.auth.infra.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * infra-redis의 스크립트 빈을 조립한다.
 */
@Configuration
public class RedisSessionConfig {

    /**
     * 리프레시 회전 원자 판정 Lua 스크립트를 로드한다.
     */
    @Bean
    public RedisScript<String> rotateSessionScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/rotate_session.lua"));
        script.setResultType(String.class);
        return script;
    }

    /**
     * 세션 생성 + 동시 세션 상한 원자 판정 Lua 스크립트를 로드한다.
     */
    @Bean
    public RedisScript<String> createSessionScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/create_session.lua"));
        script.setResultType(String.class);
        return script;
    }

    /**
     * 전 세션 원자 무효화 Lua 스크립트를 로드한다.
     */
    @Bean
    public RedisScript<Long> revokeAllSessionsScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/revoke_all_sessions.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
