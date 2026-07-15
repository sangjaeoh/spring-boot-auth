package com.example.auth.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
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
 * 인증코드 재발송 쿨다운 E2E: 같은 대상으로의 즉시 재발급이 429로 거부됨을 기본 쿨다운 설정으로
 * 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ResendCooldownE2EIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AccountProvisioningFacade provisioning;

    @Test
    void rejectsImmediateResendToSameTargetWith429() {
        String email = "cooldown@example.com";
        provisioning.provision(email, "secret123");

        assertThat(initiateReset(email).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<String> resend = initiateReset(email);

        assertThat(resend.getStatusCode().value()).isEqualTo(429);
        assertThat(String.valueOf(resend.getBody())).contains("AUTH_RATE_LIMITED");
    }

    private ResponseEntity<String> initiateReset(String email) {
        return rest.postForEntity(
                "/auth/password/reset/initiate", new PasswordResetInitiateRequest(email), String.class);
    }
}
