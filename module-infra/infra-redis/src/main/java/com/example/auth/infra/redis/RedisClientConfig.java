package com.example.auth.infra.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lettuce 연결단 실패 거동을 조립한다 — fail-closed 지연을 명령 타임아웃뿐 아니라 연결 수립·재연결
 * 구간까지 유계로 만든다.
 *
 * <p>{@code spring.data.redis.timeout}(명령 타임아웃)은 이미 수립된 연결에만 적용된다. 연결 수립이 멈추는
 * 노드(부분 failover)는 connectTimeout이 자르고, 연결 끊김 중 신규 명령은 재연결 버퍼에 큐잉되지 않고 즉시
 * 거부해(REJECT_COMMANDS) 세션 검증·무효화가 버퍼 뒤에서 무한 대기하지 않게 한다.
 */
@Configuration
public class RedisClientConfig {

    /**
     * connectTimeout·disconnectedBehavior(REJECT_COMMANDS)를 담은 {@link ClientOptions} 커스터마이저를
     * 등록한다. builder의 clientOptions를 통째로 대체하므로 Boot가 속성으로 조립하던 connectTimeout·명령
     * 타임아웃 강제(TimeoutOptions)도 여기서 함께 조립한다.
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceFailureBehaviorCustomizer(
            @Value("${spring.data.redis.connect-timeout:250ms}") Duration connectTimeout) {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .socketOptions(
                        SocketOptions.builder().connectTimeout(connectTimeout).build())
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .timeoutOptions(TimeoutOptions.enabled())
                .build());
    }
}
