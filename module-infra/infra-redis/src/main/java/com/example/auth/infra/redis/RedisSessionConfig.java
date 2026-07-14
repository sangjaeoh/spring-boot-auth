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
}
