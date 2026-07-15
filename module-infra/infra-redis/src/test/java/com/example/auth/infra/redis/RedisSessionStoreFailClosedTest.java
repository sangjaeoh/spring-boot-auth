package com.example.auth.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
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
 * Redis 불가 시 fail-closed 계약을 검증한다 — 검증은 거부(false), 쓰기·회전은 503으로 실패해 토큰을
 * 발급하지 않는다. 살아있는 Redis 없이 닫힌 포트로 연결 실패({@code DataAccessException})를 유도한다.
 */
class RedisSessionStoreFailClosedTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final Duration GRACE = Duration.ofSeconds(10);
    // 닫힌 포트는 즉시 ECONNREFUSED지만, close 후 다른 프로세스가 포트를 잡는 TOCTOU에 대비해 짧은
    // 타임아웃을 명시 상한으로 둔다(테스트가 무한 대기하지 않게).
    private static final Duration FAST_TIMEOUT = Duration.ofMillis(500);

    private LettuceConnectionFactory factory;
    private RedisSessionStore store;

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

        DefaultRedisScript<String> rotateScript = new DefaultRedisScript<>();
        rotateScript.setLocation(new ClassPathResource("redis/rotate_session.lua"));
        rotateScript.setResultType(String.class);

        store = new RedisSessionStore(template, rotateScript);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void validateFailsClosedWhenStoreUnreachable() {
        assertThat(store.validate(UUID.randomUUID(), UUID.randomUUID(), Instant.now()))
                .isFalse();
    }

    @Test
    void rotateFailsWith503WhenStoreUnreachable() {
        assertThatThrownBy(() -> store.rotate("presented", "new", "plain", Instant.now(), GRACE, TTL))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
    }

    @Test
    void createFailsWith503WhenStoreUnreachable() {
        assertThatThrownBy(() -> store.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        "1.2.3.4",
                        "agent",
                        "jti",
                        "plain",
                        Instant.now(),
                        TTL))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
    }

    @Test
    void revokeFailsWith503WhenStoreUnreachable() {
        assertThatThrownBy(() -> store.revoke(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
    }

    @Test
    void revokeAllFailsWith503WhenStoreUnreachable() {
        assertThatThrownBy(() -> store.revokeAll(UUID.randomUUID()))
                .isInstanceOf(AuthException.class)
                .extracting(e -> ((AuthException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
    }
}
