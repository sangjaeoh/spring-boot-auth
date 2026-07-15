package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.NotificationPreferenceResponse;
import com.example.auth.app.api.presentation.v1.NotificationPreferenceUpdateRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.user.entity.NotificationCategory;
import com.example.auth.domain.user.entity.NotificationChannel;
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
 * 알림 수신설정 API E2E: 매트릭스 조회·채널×카테고리 opt-in/out·SECURITY 최소 연락 채널 유지 강제를
 * 실 PostgreSQL·Redis에 대해 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class NotificationPreferenceApiE2EIT {

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
    void returnsFullMatrixWithMarketingOffByDefault() {
        provisioning.provision("pref-matrix@example.com", "secret123");
        TokenResponse tokens = login("pref-matrix@example.com");

        NotificationPreferenceResponse response = getPreferences(tokens);

        assertThat(response.entries())
                .hasSize(NotificationChannel.values().length * NotificationCategory.values().length);
        assertThat(response.entries())
                .allSatisfy(entry ->
                        assertThat(entry.allowed()).isEqualTo(entry.category() != NotificationCategory.MARKETING));
    }

    @Test
    void disallowIsReflectedInMatrix() {
        provisioning.provision("pref-optout@example.com", "secret123");
        TokenResponse tokens = login("pref-optout@example.com");

        ResponseEntity<Void> update = rest.exchange(
                "/users/me/notification-preferences/EMAIL/ACCOUNT",
                HttpMethod.PUT,
                withBearer(tokens, new NotificationPreferenceUpdateRequest(false)),
                Void.class);
        assertThat(update.getStatusCode().value()).isEqualTo(204);

        NotificationPreferenceResponse response = getPreferences(tokens);
        assertThat(response.entries())
                .filteredOn(entry -> entry.channel() == NotificationChannel.EMAIL
                        && entry.category() == NotificationCategory.ACCOUNT)
                .singleElement()
                .satisfies(entry -> assertThat(entry.allowed()).isFalse());
    }

    @Test
    void rejectsDisallowingLastSecurityContactChannel() {
        provisioning.provision("pref-security@example.com", "secret123");
        TokenResponse tokens = login("pref-security@example.com");

        ResponseEntity<Void> disallowEmail = rest.exchange(
                "/users/me/notification-preferences/EMAIL/SECURITY",
                HttpMethod.PUT,
                withBearer(tokens, new NotificationPreferenceUpdateRequest(false)),
                Void.class);
        assertThat(disallowEmail.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<String> disallowSms = rest.exchange(
                "/users/me/notification-preferences/SMS/SECURITY",
                HttpMethod.PUT,
                withBearer(tokens, new NotificationPreferenceUpdateRequest(false)),
                String.class);

        assertThat(disallowSms.getStatusCode().value()).isEqualTo(400);
        assertThat(requireNonNull(disallowSms.getBody())).contains("USER_SECURITY_CHANNEL_REQUIRED");
    }

    private NotificationPreferenceResponse getPreferences(TokenResponse tokens) {
        ResponseEntity<NotificationPreferenceResponse> response = rest.exchange(
                "/users/me/notification-preferences",
                HttpMethod.GET,
                withBearer(tokens, null),
                NotificationPreferenceResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private TokenResponse login(String email) {
        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, "secret123", DeviceBindingRequestFixture.webDevice()),
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
