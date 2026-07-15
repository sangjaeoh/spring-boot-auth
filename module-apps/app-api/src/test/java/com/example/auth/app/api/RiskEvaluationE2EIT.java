package com.example.auth.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.facade.AuthFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.domain.auth.entity.LoginAttempt;
import com.example.auth.domain.auth.entity.LoginResult;
import com.example.auth.domain.auth.repository.LoginAttemptRepository;
import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.external.notification.MockNotificationSender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 이상탐지 E2E: 신규 기기·신규 지역 로그인이 {@code riskScore}·{@code countryCode}로 기록되고, 신규
 * 지역 감지가 보안 알림으로 발송됨을 실 인프라(Mock GeoIP — TEST-NET-2 대역=US)로 검증한다.
 *
 * <p>컨트롤러는 {@code remoteAddr}를 IP로 쓰므로 HTTP로는 IP를 바꿀 수 없어 파사드 진입점으로
 * 검증한다(파사드 이하 전 구간 실 배선).
 */
@SpringBootTest
@Testcontainers
class RiskEvaluationE2EIT {

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
    private AuthFacade authFacade;

    @Autowired
    private AccountProvisioningFacade provisioning;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private MockNotificationSender notificationSender;

    @Test
    void newDeviceLoginRecordsRiskScore() {
        String email = "risk-device@example.com";
        UUID userId = provisioning.provision(email, "secret123");

        authFacade.login(email, "secret123", DeviceBindingRequestFixture.webDevice("fp-risk-1"), "203.0.113.10", null);

        LoginAttempt attempt = latestSuccess(userId);
        assertThat(attempt.getRiskScore()).isEqualTo(40);
        assertThat(attempt.getCountryCode()).isEqualTo("KR");
    }

    @Test
    void newLocationLoginRaisesRiskScoreAndSendsSecurityNotification() {
        String email = "risk-location@example.com";
        UUID userId = provisioning.provision(email, "secret123");
        authFacade.login(email, "secret123", DeviceBindingRequestFixture.webDevice("fp-risk-2"), "203.0.113.10", null);

        // 같은 기기, 해외(TEST-NET-2 → US) IP — 최근 성공 이력(KR)에 없는 국가.
        authFacade.login(email, "secret123", DeviceBindingRequestFixture.webDevice("fp-risk-2"), "198.51.100.7", null);

        LoginAttempt attempt = latestSuccess(userId);
        assertThat(attempt.getCountryCode()).isEqualTo("US");
        assertThat(attempt.getRiskScore()).isEqualTo(40);
        assertThat(notificationSender.sentTo(email)).anySatisfy(sent -> {
            assertThat(sent.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(sent.templateId()).isEqualTo("security.new-location");
        });
    }

    private LoginAttempt latestSuccess(UUID userId) {
        List<LoginAttempt> attempts =
                loginAttemptRepository.findTop20ByUserIdAndResultOrderByAtDesc(userId, LoginResult.SUCCESS);
        assertThat(attempts).isNotEmpty();
        return attempts.getFirst();
    }
}
