package com.example.auth.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.VerificationResult;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 인증코드 스토어의 Redis 불가 fail-closed 계약을 검증한다 — 발급은 503으로 실패해 코드 없는 챌린지가
 * 성공으로 오보되지 않고, 검증은 NOT_FOUND로 거부된다. 살아있는 Redis 없이 닫힌 포트로 연결 실패를 유도한다.
 */
class RedisChallengeStoreFailClosedTest {

    private static final Duration TTL = Duration.ofMinutes(5);
    // 닫힌 포트는 즉시 ECONNREFUSED지만, close 후 다른 프로세스가 포트를 잡는 TOCTOU에 대비해 짧은
    // 타임아웃을 명시 상한으로 둔다(테스트가 무한 대기하지 않게).
    private static final Duration FAST_TIMEOUT = Duration.ofMillis(500);

    private LettuceConnectionFactory factory;
    private RedisVerificationChallengeStore store;

    @BeforeEach
    void setUp() throws IOException {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        } // 닫힘 → 이 포트는 리슨하지 않는다.

        SocketOptions socketOptions =
                SocketOptions.builder().connectTimeout(FAST_TIMEOUT).build();
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(FAST_TIMEOUT)
                .shutdownTimeout(Duration.ZERO)
                .clientOptions(
                        ClientOptions.builder().socketOptions(socketOptions).build())
                .build();
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", deadPort), clientConfig);
        factory.afterPropertiesSet();
        factory.start();

        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        DefaultRedisScript<String> verifyScript = new DefaultRedisScript<>();
        verifyScript.setLocation(new ClassPathResource("redis/verify_challenge.lua"));
        verifyScript.setResultType(String.class);

        store = new RedisVerificationChallengeStore(template, verifyScript);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void issueFailsWith503WhenStoreUnreachable() {
        assertThatThrownBy(() -> store.issue("challenge-1", UUID.randomUUID(), "codehash", TTL, 5))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
    }

    @Test
    void verifyFailsClosedWhenStoreUnreachable() {
        assertThat(store.verify("challenge-1", "codehash").status())
                .isEqualTo(VerificationResult.Status.NOT_FOUND);
    }
}
