package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.RefreshRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.generic.info.AuditLogInfo;
import com.example.auth.domain.generic.service.AuditLogReader;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 인증 이벤트 감사 트레일 E2E: 로그인 실패/성공·재발급·로그아웃이 각각 감사 로그에 append됨을 실 인프라로
 * 검증한다(in-process 전달은 커밋 후 동기 — 응답 수신 시점에 감사 행이 확정돼 있다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class AuthAuditTrailE2EIT {

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

    @Autowired
    private AuditLogReader auditLogReader;

    @Test
    void authEventsAreAppendedToAuditTrail() {
        String email = "audit-user@example.com";
        UUID userId = provisioning.provision(email, "secret123");

        ResponseEntity<String> failed = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, "wrong-password1", DeviceBindingRequestFixture.webDevice()),
                String.class);
        assertThat(failed.getStatusCode().value()).isEqualTo(401);

        ResponseEntity<TokenResponse> loginResponse = login(email, "secret123");
        assertThat(loginResponse.getStatusCode().value()).isEqualTo(200);
        TokenResponse tokens = requireNonNull(loginResponse.getBody());

        ResponseEntity<TokenResponse> rotated = rest.postForEntity(
                "/auth/token/refresh", new RefreshRequest(tokens.refreshToken()), TokenResponse.class);
        assertThat(rotated.getStatusCode().value()).isEqualTo(200);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(requireNonNull(rotated.getBody()).accessToken());
        ResponseEntity<Void> logout =
                rest.exchange("/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(logout.getStatusCode().value()).isEqualTo(204);

        List<AuditLogInfo> trail =
                auditLogReader.getPage(userId.toString(), PageRequest.of(0, 20)).getContent();
        assertThat(trail)
                .extracting(AuditLogInfo::action)
                .contains("auth.login-failed", "auth.login", "auth.token-refreshed", "auth.session-revoked");
        assertThat(trail).allSatisfy(entry -> assertThat(entry.actor()).isEqualTo(userId.toString()));
    }

    private ResponseEntity<TokenResponse> login(String email, String password) {
        return rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, password, DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
    }
}
