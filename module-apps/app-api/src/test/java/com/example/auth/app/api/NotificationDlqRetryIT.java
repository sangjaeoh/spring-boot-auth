package com.example.auth.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.generic.entity.Notification;
import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.domain.generic.entity.NotificationStatus;
import com.example.auth.domain.generic.port.NotificationSender;
import com.example.auth.domain.generic.repository.NotificationRepository;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 발송 실패 재시도 E2E: 첫 발송이 실패하면 FAILED 이력이 남고 소비 실패가 DLQ로 격리되며, DLQ 재시도가
 * 같은 알림 행을 재발송(SENT·retryCount 증가)으로 수렴시킴을 실 인프라에 대해 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class NotificationDlqRetryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // DLQ 재시도를 테스트 시간 안으로 압축한다(지연 0, 짧은 폴링).
        registry.add("messaging.dlq.base-retry-delay", () -> "PT0S");
        registry.add("messaging.dlq.poll-interval", () -> "PT0.2S");
    }

    @TestConfiguration
    static class FailOnceSenderConfig {

        @Bean
        @Primary
        FailOnceNotificationSender failOnceNotificationSender() {
            return new FailOnceNotificationSender();
        }
    }

    /**
     * 첫 호출만 실패하는 발송 어댑터다 — 일시 벤더 장애를 재현한다.
     */
    static class FailOnceNotificationSender implements NotificationSender {

        private final AtomicBoolean failedOnce = new AtomicBoolean();
        private final List<String> delivered = new CopyOnWriteArrayList<>();

        @Override
        public void send(NotificationChannel channel, String target, String templateId, String payload) {
            if (failedOnce.compareAndSet(false, true)) {
                throw new IllegalStateException("일시 발송 장애(테스트)");
            }
            delivered.add(templateId);
        }

        List<String> delivered() {
            return List.copyOf(delivered);
        }
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AccountProvisioningFacade provisioning;

    @Autowired
    private FailOnceNotificationSender sender;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void retriesFailedDispatchThroughDlqUntilSent() throws Exception {
        String email = "dlq-retry@example.com";
        provisioning.provision(email, "secret123");

        // 신규 기기 로그인 → 보안 알림 발송 시도(첫 발송은 실패) — 로그인 자체는 성공해야 한다(발송 격리).
        ResponseEntity<TokenResponse> login = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, "secret123", DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);

        // 보안 기본 수신설정은 EMAIL·SMS 둘 다 허용이라 채널별 2건이 발송된다 — 실패한 첫 채널만
        // DLQ 재시도로 retryCount가 오른 SENT로 수렴하고, 나머지 채널은 즉시 SENT(retryCount 0)다.
        Notification retried = awaitRetriedSentNotification();
        assertThat(retried.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getTemplateId()).isEqualTo("security.new-device");
        assertThat(sender.delivered()).contains("security.new-device");
    }

    private Notification awaitRetriedSentNotification() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Notification sent = notificationRepository.findAll().stream()
                    .filter(notification -> notification.getStatus() == NotificationStatus.SENT)
                    .filter(notification -> notification.getRetryCount() >= 1)
                    .findFirst()
                    .orElse(null);
            if (sent != null) {
                return sent;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("DLQ 재시도 후에도 재시도된 SENT 알림이 없다");
    }
}
