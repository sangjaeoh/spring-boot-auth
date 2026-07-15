package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.auth.entity.LifecycleStatus;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import com.example.auth.domain.auth.repository.DeviceRepository;
import com.example.auth.domain.auth.repository.PasswordCredentialRepository;
import com.example.auth.domain.user.exception.RejoinCooldownException;
import com.example.auth.domain.user.repository.NotificationPreferenceRepository;
import com.example.auth.domain.user.repository.UserRepository;
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
 * 탈퇴 E2E: 탈퇴 직후 기존 세션 전멸·신규 로그인 fail-closed, PII 즉시 파기(평문 잔존 없음), 자격증명/
 * 소셜/기기 정리, 재가입 쿨다운 거부를 실 PostgreSQL·Redis에 대해 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class WithdrawalApiE2EIT {

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
    private UserRepository userRepository;

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Autowired
    private PasswordCredentialRepository passwordCredentialRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Test
    void withdrawalKillsAllSessionsAndClosesFreshLoginWindow() {
        UUID userId = provisioning.provision("goner@example.com", "secret123");
        TokenResponse first = login("goner@example.com", "secret123");
        TokenResponse second = login("goner@example.com", "secret123");

        ResponseEntity<Void> withdraw =
                rest.exchange("/users/me", HttpMethod.DELETE, withBearer(first, null), Void.class);
        assertThat(withdraw.getStatusCode().value()).isEqualTo(204);

        // 기존 세션 전멸 — 유효기간이 남은 Access도 즉시 401.
        assertThat(status("/auth/me", first)).isEqualTo(401);
        assertThat(status("/auth/me", second)).isEqualTo(401);

        // 신규 로그인 fail-closed — 파기된 정체성에 fresh 세션이 발급되지 않는다.
        ResponseEntity<String> freshLogin = rest.postForEntity(
                "/auth/login",
                new LoginRequest("goner@example.com", "secret123", DeviceBindingRequestFixture.webDevice()),
                String.class);
        assertThat(freshLogin.getStatusCode().value()).isEqualTo(401);

        // PII 즉시 파기 — 평문·암호문·blind index·CI 해시 잔존 없음(법정 보존분 제외).
        var user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getProfile()).isNull();
        assertThat(user.getContact()).isNull();
        assertThat(user.getContactPhoneBidx()).isNull();
        assertThat(user.getCiHash()).isNull();

        // 인증 측 정리 — 스냅샷 WITHDRAWN + loginEmail 파기 + 자격증명/기기/수신설정 제거.
        var account = authAccountRepository.findById(userId).orElseThrow();
        assertThat(account.getUserStatus()).isEqualTo(LifecycleStatus.WITHDRAWN);
        assertThat(account.getLoginEmail()).isNull();
        assertThat(passwordCredentialRepository.findById(userId)).isEmpty();
        assertThat(deviceRepository.findAllByUserIdOrderByLastAccessedAtDesc(userId))
                .isEmpty();
        assertThat(notificationPreferenceRepository.findById(userId)).isEmpty();
    }

    @Test
    void rejoinWithinCooldownIsRejectedWithRemainingDays() {
        provisioning.provision("rejoiner@example.com", "secret123");
        TokenResponse tokens = login("rejoiner@example.com", "secret123");
        assertThat(rest.exchange("/users/me", HttpMethod.DELETE, withBearer(tokens, null), Void.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(204);

        // 같은 정체성(같은 이메일 → Mock CI 결정적 파생)의 재가입 — 쿨다운 30일 내 거부 + 잔여일 안내.
        assertThatThrownBy(() -> provisioning.provision("rejoiner@example.com", "secret123"))
                .isInstanceOf(RejoinCooldownException.class)
                .satisfies(e ->
                        assertThat(((RejoinCooldownException) e).properties()).containsEntry("remainingDays", 30L));
    }

    private int status(String path, TokenResponse tokens) {
        return rest.exchange(path, HttpMethod.GET, withBearer(tokens, null), String.class)
                .getStatusCode()
                .value();
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
