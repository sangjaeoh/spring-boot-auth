package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.AgreeConsentRequest;
import com.example.auth.app.api.presentation.v1.ConsentOverviewResponse;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.user.entity.NotificationCategory;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.NotificationPreference;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.repository.NotificationPreferenceRepository;
import com.example.auth.domain.user.service.TermsVersionAppender;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 약관 동의 E2E: 필수 새 버전 발행→재동의 게이트, 필수 철회 거부, 마케팅 동의↔수신설정 동기화를 실
 * PostgreSQL·Redis에 대해 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ConsentApiE2EIT {

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
    private TermsVersionAppender termsVersionAppender;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void requiredNewVersionRequiresReconsentAndAgreeClearsGate() {
        provisioning.provision("terms-gate@example.com", "secret123");
        TokenResponse tokens = login("terms-gate@example.com", "secret123");

        ConsentOverviewResponse before = overview(tokens);
        assertThat(before.states()).hasSize(3);
        assertThat(before.pendingRequired()).isEmpty();

        int newVersion = termsVersionAppender.publish(TermsType.SERVICE, "서비스 약관 개정판", Instant.now());

        ConsentOverviewResponse gated = overview(tokens);
        assertThat(gated.pendingRequired()).hasSize(1);
        assertThat(gated.pendingRequired().getFirst().termsType()).isEqualTo("SERVICE");
        assertThat(gated.pendingRequired().getFirst().requiredVersion()).isEqualTo(newVersion);

        ResponseEntity<Void> agree = rest.exchange(
                "/users/me/consents",
                HttpMethod.POST,
                withBearer(tokens, new AgreeConsentRequest(TermsType.SERVICE, newVersion, null)),
                Void.class);
        assertThat(agree.getStatusCode().value()).isEqualTo(204);

        assertThat(overview(tokens).pendingRequired()).isEmpty();
    }

    @Test
    void requiredConsentWithdrawalIsRejectedWithWithdrawalGuidance() {
        provisioning.provision("required-withdraw@example.com", "secret123");
        TokenResponse tokens = login("required-withdraw@example.com", "secret123");

        ResponseEntity<String> response =
                rest.exchange("/users/me/consents/AGE14", HttpMethod.DELETE, withBearer(tokens, null), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(requireNonNull(response.getBody())).contains("USER_REQUIRED_CONSENT_WITHDRAWAL");
    }

    @Test
    void marketingConsentSyncsNotificationPreferenceMatrix() {
        UUID userId = provisioning.provision("marketing-sync@example.com", "secret123");
        TokenResponse tokens = login("marketing-sync@example.com", "secret123");

        // 가입 시 마케팅 미동의 → 매트릭스 마케팅 전 채널 거부.
        assertThat(marketingAllowed(userId, NotificationChannel.EMAIL)).isFalse();

        ResponseEntity<Void> agree = rest.exchange(
                "/users/me/consents",
                HttpMethod.POST,
                withBearer(tokens, new AgreeConsentRequest(TermsType.MARKETING, 1, NotificationChannel.EMAIL)),
                Void.class);
        assertThat(agree.getStatusCode().value()).isEqualTo(204);
        assertThat(marketingAllowed(userId, NotificationChannel.EMAIL)).isTrue();
        assertThat(marketingAllowed(userId, NotificationChannel.SMS)).isFalse();

        ResponseEntity<Void> withdraw =
                rest.exchange("/users/me/consents/MARKETING", HttpMethod.DELETE, withBearer(tokens, null), Void.class);
        assertThat(withdraw.getStatusCode().value()).isEqualTo(204);
        assertThat(marketingAllowed(userId, NotificationChannel.EMAIL)).isFalse();
    }

    @Test
    void consentApiRequiresAuthentication() {
        ResponseEntity<String> response = rest.getForEntity("/users/me/consents", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    private ConsentOverviewResponse overview(TokenResponse tokens) {
        ResponseEntity<ConsentOverviewResponse> response = rest.exchange(
                "/users/me/consents", HttpMethod.GET, withBearer(tokens, null), ConsentOverviewResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private boolean marketingAllowed(UUID userId, NotificationChannel channel) {
        // 엔티티 지연 컬렉션 접근이라 트랜잭션 안에서 판정까지 끝낸다(테스트 전용 접근).
        return Boolean.TRUE.equals(new TransactionTemplate(transactionManager).execute(status -> {
            NotificationPreference preference =
                    notificationPreferenceRepository.findById(userId).orElseThrow();
            return preference.isAllowed(channel, NotificationCategory.MARKETING);
        }));
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
