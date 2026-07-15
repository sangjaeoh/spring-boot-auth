package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.NotificationPreferenceUpdateRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.external.notification.MockNotificationSender;
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
 * 보안 이벤트 구독→발송 판정 E2E: 신규 기기 로그인이 보안 알림을 유저 연락처(contactEmail)로 발송하고,
 * SECURITY 채널 opt-out 시에도 남은 연락 채널(SMS)로 발송됨(수신거부 무시)을 실 인프라에 대해 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class NotificationDispatchE2EIT {

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
    private MockNotificationSender notificationSender;

    @Test
    void newDeviceLoginSendsSecurityNotificationToContactEmail() {
        String email = "dispatch-email@example.com";
        provisioning.provision(email, "secret123");

        login(email, "fp-dispatch-first");

        assertThat(notificationSender.sentTo(email)).anySatisfy(sent -> {
            assertThat(sent.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(sent.templateId()).isEqualTo("security.new-device");
        });
    }

    @Test
    void securityNotificationIgnoresOptOutAndFallsBackToRemainingContactChannel() {
        String email = "dispatch-optout@example.com";
        provisioning.provision(email, "secret123");
        TokenResponse tokens = login(email, "fp-optout-first");

        ResponseEntity<Void> disallowEmail = rest.exchange(
                "/users/me/notification-preferences/EMAIL/SECURITY",
                HttpMethod.PUT,
                withBearer(tokens, new NotificationPreferenceUpdateRequest(false)),
                Void.class);
        assertThat(disallowEmail.getStatusCode().value()).isEqualTo(204);

        login(email, "fp-optout-second");

        // opt-out된 EMAIL 대신 남은 연락 채널(SMS)로 발송된다 — 시드 전화는 이메일에서 결정적으로 파생.
        assertThat(notificationSender.sentTo(seedPhone(email))).anySatisfy(sent -> {
            assertThat(sent.channel()).isEqualTo(NotificationChannel.SMS);
            assertThat(sent.templateId()).isEqualTo("security.new-device");
        });
        // opt-out 이후의 신규 기기 알림은 EMAIL로 나가지 않는다(첫 로그인 1건만 존재).
        assertThat(notificationSender.sentTo(email))
                .filteredOn(sent -> sent.templateId().equals("security.new-device"))
                .hasSize(1);
    }

    private TokenResponse login(String email, String fingerprint) {
        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, "secret123", DeviceBindingRequestFixture.webDevice(fingerprint)),
                TokenResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private HttpEntity<Object> withBearer(TokenResponse tokens, @Nullable Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return new HttpEntity<>(body, headers);
    }

    // AccountProvisioningFacade.seedPhone과 동일한 파생 — 시드 회원의 contactPhone.
    private static String seedPhone(String loginEmail) {
        return "+8210%08d".formatted(Math.floorMod(loginEmail.hashCode(), 100_000_000));
    }
}
