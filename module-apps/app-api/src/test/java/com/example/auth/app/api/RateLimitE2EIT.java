package com.example.auth.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.PasswordResetInitiateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 레이트리밋 E2E: 로그인 IP 한도 초과 429, 인증코드 채널별 일일 발송 한도 초과 429를 실 인프라로
 * 검증한다(테스트 한도로 압축 — 재발송 쿨다운은 {@code ResendCooldownE2EIT}가 검증).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class RateLimitE2EIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("auth.rate-limit.login.ip-limit", () -> 3);
        registry.add("auth.verification.resend-cooldown-seconds", () -> 0);
        registry.add("auth.verification.daily-limit-email", () -> 2);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AccountProvisioningFacade provisioning;

    @Test
    void rejectsLoginBeyondIpLimitWith429() {
        for (int i = 0; i < 3; i++) {
            assertThat(loginAttempt("rl-login@example.com").getStatusCode().value())
                    .isEqualTo(401);
        }

        ResponseEntity<String> exceeded = loginAttempt("rl-login@example.com");

        assertThat(exceeded.getStatusCode().value()).isEqualTo(429);
        assertThat(String.valueOf(exceeded.getBody())).contains("AUTH_RATE_LIMITED");
    }

    @Test
    void rejectsCodeIssueBeyondDailyLimitWith429() {
        String email = "rl-daily@example.com";
        provisioning.provision(email, "secret123");

        assertThat(initiateReset(email).getStatusCode().value()).isEqualTo(200);
        assertThat(initiateReset(email).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> exceeded = initiateReset(email);

        assertThat(exceeded.getStatusCode().value()).isEqualTo(429);
        assertThat(String.valueOf(exceeded.getBody())).contains("AUTH_RATE_LIMITED");
    }

    private ResponseEntity<String> loginAttempt(String email) {
        return rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, "wrong-password1", DeviceBindingRequestFixture.webDevice()),
                String.class);
    }

    private ResponseEntity<String> initiateReset(String email) {
        return rest.postForEntity(
                "/auth/password/reset/initiate", new PasswordResetInitiateRequest(email), String.class);
    }
}
