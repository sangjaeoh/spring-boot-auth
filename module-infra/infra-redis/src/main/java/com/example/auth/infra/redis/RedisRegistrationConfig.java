package com.example.auth.infra.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 온보딩 세션 스토어의 스크립트 빈을 조립한다.
 */
@Configuration
public class RedisRegistrationConfig {

    /**
     * 온보딩 세션 스텝 마킹(EXISTS 가드) Lua 스크립트를 로드한다.
     */
    @Bean
    public RedisScript<Long> markRegistrationScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/mark_registration.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
