package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.MeResponse;
import com.example.auth.app.api.presentation.v1.RefreshRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.auth.service.AuthAccountModifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.List;
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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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

    @Autowired
    private AuthAccountModifier authAccountModifier;

    @Test
    void loginAuthenticatedRequestThenLogoutRevokesImmediately() {
        UUID userId = provisioning.provision("alice@example.com", "secret123");

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
        provisioning.provision("bob@example.com", "secret123");

        ResponseEntity<String> response = rest.postForEntity(
                "/auth/login",
                new LoginRequest("bob@example.com", "wrongpass1", DeviceBindingRequestFixture.webDevice()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void nonexistentAccountLoginIsIndistinguishableFromWrongPassword() {
        provisioning.provision("parity@example.com", "secret123");

        ResponseEntity<String> wrongPassword = rest.postForEntity(
                "/auth/login",
                new LoginRequest("parity@example.com", "wrongpass1", DeviceBindingRequestFixture.webDevice()),
                String.class);
        ResponseEntity<String> nonexistent = rest.postForEntity(
                "/auth/login",
                new LoginRequest("no-such-user@example.com", "wrongpass1", DeviceBindingRequestFixture.webDevice()),
                String.class);

        // 열거 저항: 존재/비존재 계정의 응답이 상태·본문까지 동일해야 계정 존재가 새지 않는다
        // (ProblemDetail은 가변 필드가 없어 본문 완전 일치로 단언한다 — docs/ops/threat-model.md).
        assertThat(nonexistent.getStatusCode().value()).isEqualTo(401);
        assertThat(nonexistent.getStatusCode()).isEqualTo(wrongPassword.getStatusCode());
        assertThat(nonexistent.getBody()).isEqualTo(wrongPassword.getBody());
    }

    @Test
    void dotlessDomainEmailIsRejectedAtBoundaryAsBadRequest() {
        // Jakarta @Email이 통과시키던 점 없는 도메인 — 경계 @Pattern이 걸러 도메인 Email IAE 500을 막는다.
        ResponseEntity<String> response = rest.postForEntity(
                "/auth/login",
                new LoginRequest("eve@nodot", "secret123", DeviceBindingRequestFixture.webDevice()),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        ResponseEntity<String> response = rest.getForEntity("/auth/me", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void refreshRotatesAndReuseRevokesFamily() {
        provisioning.provision("carol@example.com", "secret123");
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

    @Test
    void jwksEndpointPublishesPublicKeysThatVerifyIssuedTokens() throws Exception {
        UUID userId = provisioning.provision("dave@example.com", "secret123");
        TokenResponse tokens = login("dave@example.com", "secret123");

        // JWKS는 인증 없이 접근 가능(permitAll).
        ResponseEntity<String> jwksResponse = rest.getForEntity("/.well-known/jwks.json", String.class);
        assertThat(jwksResponse.getStatusCode().value()).isEqualTo(200);

        JWKSet published = JWKSet.parse(requireNonNull(jwksResponse.getBody()));
        assertThat(published.getKeys()).isNotEmpty();
        assertThat(published.getKeys())
                .allSatisfy(key -> assertThat(key.isPrivate()).isFalse());

        // 발급 토큰의 kid가 게시 키에 존재하고, 게시된 공개키로 실제 검증이 성립한다(게시 계약의 실질 증명).
        String tokenKid = SignedJWT.parse(tokens.accessToken()).getHeader().getKeyID();
        assertThat(published.getKeyByKeyId(tokenKid)).isNotNull();

        JWKSource<SecurityContext> publishedSource = (selector, context) -> selector.select(published);
        Jwt decoded = NimbusJwtDecoder.withJwkSource(publishedSource)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build()
                .decode(tokens.accessToken());
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void accessTokenRolesClaimFollowsRoleProjectionOnReissue() throws Exception {
        UUID userId = provisioning.provision("erin@example.com", "secret123");
        TokenResponse tokens = login("erin@example.com", "secret123");
        assertThat(rolesClaim(tokens.accessToken())).containsExactly("USER");

        // RoleChanged 소비가 갱신하는 roles 투영을 직접 반영한다(발행측 관리자 API는 app-admin 소유).
        // 재발급 시 반영 결정: 새 Access부터 새 역할, 기존 Access는 TTL까지 이전 역할.
        authAccountModifier.applyRoles(userId, List.of("ADMIN", "USER"), Instant.now());

        ResponseEntity<TokenResponse> rotated = rest.postForEntity(
                "/auth/token/refresh", new RefreshRequest(tokens.refreshToken()), TokenResponse.class);
        assertThat(rotated.getStatusCode().value()).isEqualTo(200);
        assertThat(rolesClaim(requireNonNull(rotated.getBody()).accessToken())).containsExactly("ADMIN", "USER");
    }

    private static List<String> rolesClaim(String accessToken) throws Exception {
        return SignedJWT.parse(accessToken).getJWTClaimsSet().getStringListClaim("roles");
    }

    private TokenResponse login(String email, String password) {
        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, password, DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private static HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }
}
