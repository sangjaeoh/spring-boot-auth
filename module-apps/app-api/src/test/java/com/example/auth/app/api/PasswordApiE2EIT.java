package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.PasswordChangeRequest;
import com.example.auth.app.api.presentation.v1.PasswordResetCompleteRequest;
import com.example.auth.app.api.presentation.v1.PasswordResetInitiateRequest;
import com.example.auth.app.api.presentation.v1.PasswordResetInitiateResponse;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.external.notification.MockVerificationCodeSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
 * 비밀번호 생명주기 E2E: 재설정(코드 검증→새 비번→전 세션 무효화)·로그인 상태 변경·재사용 금지를 실 인프라로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class PasswordApiE2EIT {

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
    private MockVerificationCodeSender verificationCodeSender;

    @Test
    void resetSetsNewPasswordAndRevokesExistingSessions() {
        String email = "reset@example.com";
        provisioning.provision(email, "secret123");
        TokenResponse tokens = login(email, "secret123");

        ResponseEntity<PasswordResetInitiateResponse> initiated = rest.postForEntity(
                "/auth/password/reset/initiate",
                new PasswordResetInitiateRequest(email),
                PasswordResetInitiateResponse.class);
        assertThat(initiated.getStatusCode().value()).isEqualTo(200);
        String challengeId = requireNonNull(initiated.getBody()).challengeId();
        String code = requireNonNull(verificationCodeSender.lastContent(email));

        ResponseEntity<Void> completed = rest.postForEntity(
                "/auth/password/reset/complete",
                new PasswordResetCompleteRequest(challengeId, code, "newpass123"),
                Void.class);
        assertThat(completed.getStatusCode().value()).isEqualTo(204);

        // 기존 세션 무효화: 재설정 전 발급된 Access는 즉시 401.
        assertThat(rest.exchange("/auth/me", HttpMethod.GET, bearer(tokens.accessToken()), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
        // 새 비번 로그인 성공, 옛 비번 거부.
        assertThat(loginStatus(email, "newpass123")).isEqualTo(200);
        assertThat(loginStatus(email, "secret123")).isEqualTo(401);
    }

    @Test
    void resetRejectsWrongCode() {
        String email = "resetwrong@example.com";
        provisioning.provision(email, "secret123");

        ResponseEntity<PasswordResetInitiateResponse> initiated = rest.postForEntity(
                "/auth/password/reset/initiate",
                new PasswordResetInitiateRequest(email),
                PasswordResetInitiateResponse.class);
        String challengeId = requireNonNull(initiated.getBody()).challengeId();

        ResponseEntity<String> completed = rest.postForEntity(
                "/auth/password/reset/complete",
                new PasswordResetCompleteRequest(challengeId, "wrong-code", "newpass123"),
                String.class);
        assertThat(completed.getStatusCode().value()).isEqualTo(400);
        // 옛 비번은 그대로 유효.
        assertThat(loginStatus(email, "secret123")).isEqualTo(200);
    }

    @Test
    void resetRejectingReuseConsumesCode() {
        String email = "resetreuse@example.com";
        provisioning.provision(email, "secret123");

        ResponseEntity<PasswordResetInitiateResponse> initiated = rest.postForEntity(
                "/auth/password/reset/initiate",
                new PasswordResetInitiateRequest(email),
                PasswordResetInitiateResponse.class);
        String challengeId = requireNonNull(initiated.getBody()).challengeId();
        String code = requireNonNull(verificationCodeSender.lastContent(email));

        // 새 비번이 현재 비번(이력 내)과 동일 → 재사용 400. 코드는 검증 단계에서 이미 소진된다.
        ResponseEntity<String> reuse = rest.postForEntity(
                "/auth/password/reset/complete",
                new PasswordResetCompleteRequest(challengeId, code, "secret123"),
                String.class);
        assertThat(reuse.getStatusCode().value()).isEqualTo(400);

        // 소진된 코드로 다른 새 비번을 재시도해도 챌린지가 없어 거부 → 재시작 필요.
        ResponseEntity<String> retry = rest.postForEntity(
                "/auth/password/reset/complete",
                new PasswordResetCompleteRequest(challengeId, code, "newpass123"),
                String.class);
        assertThat(retry.getStatusCode().value()).isEqualTo(400);
        // 재설정 미완료이므로 옛 비번 유효.
        assertThat(loginStatus(email, "secret123")).isEqualTo(200);
    }

    @Test
    void changeSetsNewPassword() {
        String email = "change@example.com";
        provisioning.provision(email, "secret123");
        TokenResponse tokens = login(email, "secret123");

        ResponseEntity<Void> changed = rest.exchange(
                "/auth/password/change",
                HttpMethod.POST,
                bearer(tokens.accessToken(), new PasswordChangeRequest("secret123", "changed123")),
                Void.class);
        assertThat(changed.getStatusCode().value()).isEqualTo(204);

        assertThat(loginStatus(email, "changed123")).isEqualTo(200);
        assertThat(loginStatus(email, "secret123")).isEqualTo(401);
    }

    @Test
    void changeRejectsWrongCurrentPassword() {
        String email = "changewrong@example.com";
        provisioning.provision(email, "secret123");
        TokenResponse tokens = login(email, "secret123");

        ResponseEntity<String> changed = rest.exchange(
                "/auth/password/change",
                HttpMethod.POST,
                bearer(tokens.accessToken(), new PasswordChangeRequest("wrongcurrent1", "changed123")),
                String.class);
        assertThat(changed.getStatusCode().value()).isEqualTo(400);
        assertThat(loginStatus(email, "secret123")).isEqualTo(200);
    }

    @Test
    void changeRejectsReuseOfCurrentPassword() {
        String email = "reuse@example.com";
        provisioning.provision(email, "secret123");
        TokenResponse tokens = login(email, "secret123");

        ResponseEntity<String> changed = rest.exchange(
                "/auth/password/change",
                HttpMethod.POST,
                bearer(tokens.accessToken(), new PasswordChangeRequest("secret123", "secret123")),
                String.class);
        assertThat(changed.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void initiateReturnsUniformlyForUnknownEmail() {
        ResponseEntity<PasswordResetInitiateResponse> initiated = rest.postForEntity(
                "/auth/password/reset/initiate",
                new PasswordResetInitiateRequest("nobody@example.com"),
                PasswordResetInitiateResponse.class);
        assertThat(initiated.getStatusCode().value()).isEqualTo(200);
        assertThat(requireNonNull(initiated.getBody()).challengeId()).isNotBlank();
    }

    private TokenResponse login(String email, String password) {
        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, password, DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private int loginStatus(String email, String password) {
        return rest.postForEntity(
                        "/auth/login",
                        new LoginRequest(email, password, DeviceBindingRequestFixture.webDevice()),
                        String.class)
                .getStatusCode()
                .value();
    }

    private static HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private static <T> HttpEntity<T> bearer(String accessToken, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(body, headers);
    }
}
