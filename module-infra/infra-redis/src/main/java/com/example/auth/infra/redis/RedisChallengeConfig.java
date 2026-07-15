package com.example.auth.infra.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 인증코드 스토어의 스크립트 빈을 조립한다.
 */
@Configuration
public class RedisChallengeConfig {

    /**
     * 인증코드 원자 검증 Lua 스크립트를 로드한다.
     */
    @Bean
    public RedisScript<String> verifyChallengeScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/verify_challenge.lua"));
        script.setResultType(String.class);
        return script;
    }
}
