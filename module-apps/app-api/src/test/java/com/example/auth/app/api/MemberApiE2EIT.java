package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.ChangeContactEmailRequest;
import com.example.auth.app.api.presentation.v1.ChangeLoginEmailRequest;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.MyInfoResponse;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
 * 내 정보 E2E: 마스킹 조회, 연락용 이메일 변경(로그인 식별자 독립), 로그인 이메일 변경(유니크·즉시 반영)을
 * 실 PostgreSQL·Redis에 대해 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class MemberApiE2EIT {

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
    void returnsMaskedMyInfo() {
        UUID userId = provisioning.provision("myinfo@example.com", "secret123");
        TokenResponse tokens = login("myinfo@example.com", "secret123");

        MyInfoResponse me = myInfo(tokens);

        assertThat(me.userId()).isEqualTo(userId);
        // 시드 프로필 "시드사용자" — 첫 글자 외 마스킹, 원문 미노출.
        assertThat(me.name()).isEqualTo("시****");
        assertThat(me.contactEmail()).isEqualTo("my***@example.com");
        assertThat(me.loginEmail()).isEqualTo("my***@example.com");
        // 시드 전화 +8210XXXXXXXX(13자) — 앞 5·뒤 4 유지, 중간 4자 마스킹.
        assertThat(me.contactPhone()).startsWith("+8210").contains("****").hasSize(13);
        assertThat(me.status()).isEqualTo("ACTIVE");
    }

    @Test
    void changesContactEmailIndependentlyOfLoginEmail() {
        provisioning.provision("contact-change@example.com", "secret123");
        TokenResponse tokens = login("contact-change@example.com", "secret123");

        ResponseEntity<Void> change = rest.exchange(
                "/users/me/contact-email",
                HttpMethod.PUT,
                withBearer(tokens, new ChangeContactEmailRequest("new-contact@example.net")),
                Void.class);
        assertThat(change.getStatusCode().value()).isEqualTo(204);

        MyInfoResponse me = myInfo(tokens);
        assertThat(me.contactEmail()).isEqualTo("ne***@example.net");
        // 로그인 식별자는 그대로 — 기존 이메일 로그인 유지.
        assertThat(me.loginEmail()).isEqualTo("co***@example.com");
        assertThat(login("contact-change@example.com", "secret123")).isNotNull();
    }

    @Test
    void changesLoginEmailAndOldIdentifierStopsWorking() {
        provisioning.provision("login-old@example.com", "secret123");
        TokenResponse tokens = login("login-old@example.com", "secret123");

        ResponseEntity<Void> change = rest.exchange(
                "/users/me/login-email",
                HttpMethod.PUT,
                withBearer(tokens, new ChangeLoginEmailRequest("login-new@example.com")),
                Void.class);
        assertThat(change.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> oldLogin = rest.postForEntity(
                "/auth/login",
                new LoginRequest("login-old@example.com", "secret123", DeviceBindingRequestFixture.webDevice()),
                String.class);
        assertThat(oldLogin.getStatusCode().value()).isEqualTo(401);

        assertThat(login("login-new@example.com", "secret123")).isNotNull();
    }

    @Test
    void rejectsDuplicateLoginEmail() {
        provisioning.provision("taken@example.com", "secret123");
        provisioning.provision("changer@example.com", "secret123");
        TokenResponse tokens = login("changer@example.com", "secret123");

        ResponseEntity<String> change = rest.exchange(
                "/users/me/login-email",
                HttpMethod.PUT,
                withBearer(tokens, new ChangeLoginEmailRequest("taken@example.com")),
                String.class);

        assertThat(change.getStatusCode().value()).isEqualTo(409);
        assertThat(requireNonNull(change.getBody())).contains("AUTH_LOGIN_EMAIL_DUPLICATE");
    }

    private MyInfoResponse myInfo(TokenResponse tokens) {
        ResponseEntity<MyInfoResponse> response =
                rest.exchange("/users/me", HttpMethod.GET, withBearer(tokens, null), MyInfoResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private TokenResponse login(String email, String password) {
        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, password, DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private HttpEntity<Object> withBearer(TokenResponse tokens, @Nullable Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return new HttpEntity<>(body, headers);
    }
}
