package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.MeResponse;
import com.example.auth.app.api.presentation.v1.RefreshRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import java.util.UUID;
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
 * 인증 핫패스 E2E: 실시간 세션 무효화 제품 명제를 실 PostgreSQL·Redis에 대해 검증한다.
 *
 * <p>로그인→O(1) 세션검증→로그아웃 후 기존 Access 즉시 401, 리프레시 회전, 재사용 감지→패밀리 전멸을 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class AuthApiE2EIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // 유예 창을 0으로 두어 재사용 감지 E2E를 결정적으로 만든다(유예 재시도는 store IT가 커버).
        registry.add("auth.refresh.grace-seconds", () -> "0");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AccountProvisioningFacade provisioning;

    @Test
    void loginAuthenticatedRequestThenLogoutRevokesImmediately() {
        UUID userId = UUID.randomUUID();
        provisioning.provision(userId, "alice@example.com", "secret123");

        TokenResponse tokens = login("alice@example.com", "secret123");

        ResponseEntity<MeResponse> me =
                rest.exchange("/auth/me", HttpMethod.GET, bearer(tokens.accessToken()), MeResponse.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(requireNonNull(me.getBody()).userId()).isEqualTo(userId);

        ResponseEntity<Void> logout =
                rest.exchange("/auth/logout", HttpMethod.POST, bearer(tokens.accessToken()), Void.class);
        assertThat(logout.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> afterLogout =
                rest.exchange("/auth/me", HttpMethod.GET, bearer(tokens.accessToken()), String.class);
        assertThat(afterLogout.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void wrongPasswordIsRejected() {
        provisioning.provision(UUID.randomUUID(), "bob@example.com", "secret123");

        ResponseEntity<String> response =
                rest.postForEntity("/auth/login", new LoginRequest("bob@example.com", "wrongpass1"), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        ResponseEntity<String> response = rest.getForEntity("/auth/me", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void refreshRotatesAndReuseRevokesFamily() {
        provisioning.provision(UUID.randomUUID(), "carol@example.com", "secret123");
        TokenResponse first = login("carol@example.com", "secret123");

        ResponseEntity<TokenResponse> rotated = rest.postForEntity(
                "/auth/token/refresh", new RefreshRequest(first.refreshToken()), TokenResponse.class);
        assertThat(rotated.getStatusCode().value()).isEqualTo(200);
        TokenResponse second = requireNonNull(rotated.getBody());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(rest.exchange("/auth/me", HttpMethod.GET, bearer(second.accessToken()), MeResponse.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(200);

        // 폐기된 옛 리프레시 재사용 → 401 + 세션 패밀리 전멸(새 access도 무효).
        ResponseEntity<String> reuse =
                rest.postForEntity("/auth/token/refresh", new RefreshRequest(first.refreshToken()), String.class);
        assertThat(reuse.getStatusCode().value()).isEqualTo(401);
        assertThat(rest.exchange("/auth/me", HttpMethod.GET, bearer(second.accessToken()), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
    }

    private TokenResponse login(String email, String password) {
        ResponseEntity<TokenResponse> response =
                rest.postForEntity("/auth/login", new LoginRequest(email, password), TokenResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private static HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
