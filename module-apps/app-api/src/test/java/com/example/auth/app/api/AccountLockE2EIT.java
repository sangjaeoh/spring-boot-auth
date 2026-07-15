package com.example.auth.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.domain.auth.entity.LockState;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.external.notification.MockNotificationSender;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 계정 잠금 E2E: 연속 실패 임계 초과 시 TEMP_LOCKED(올바른 비밀번호도 401 — 열거 저항 균일 응답) +
 * 보안 알림 발송, 쿨다운 경과 후 자동 해제, 성공 로그인 시 실패 카운터 리셋을 실 인프라로 검증한다
 * (임계 3회·쿨다운 1초로 압축).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class AccountLockE2EIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("auth.lock.failure-threshold", () -> 3);
        registry.add("auth.lock.cooldown-seconds", () -> 1);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AccountProvisioningFacade provisioning;

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Autowired
    private MockNotificationSender notificationSender;

    @Test
    void locksAfterConsecutiveFailuresAndAutoReleasesAfterCooldown() throws Exception {
        String email = "lock-release@example.com";
        UUID userId = provisioning.provision(email, "secret123");

        for (int i = 0; i < 3; i++) {
            assertThat(login(email, "wrong-password1").getStatusCode().value()).isEqualTo(401);
        }
        assertThat(authAccountRepository.findById(userId).orElseThrow().getLockState())
                .isEqualTo(LockState.TEMP_LOCKED);
        // 잠금 중에는 올바른 비밀번호도 사유 미구분 401이다.
        assertThat(login(email, "secret123").getStatusCode().value()).isEqualTo(401);
        // 잠금은 보안 알림으로 통지된다(opt-out 무시 대상).
        assertThat(notificationSender.sentTo(email)).anySatisfy(sent -> {
            assertThat(sent.channel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(sent.templateId()).isEqualTo("security.account-locked");
        });

        Thread.sleep(1_500);

        assertThat(login(email, "secret123").getStatusCode().value()).isEqualTo(200);
        assertThat(authAccountRepository.findById(userId).orElseThrow().getLockState())
                .isEqualTo(LockState.NONE);
    }

    @Test
    void successfulLoginResetsFailureCounter() {
        String email = "lock-reset@example.com";
        UUID userId = provisioning.provision(email, "secret123");

        assertThat(login(email, "wrong-password1").getStatusCode().value()).isEqualTo(401);
        assertThat(login(email, "wrong-password1").getStatusCode().value()).isEqualTo(401);
        assertThat(login(email, "secret123").getStatusCode().value()).isEqualTo(200);

        // 성공이 카운터를 리셋했으므로 이어지는 2회 실패는 임계(3) 미만이다 — 잠기지 않는다.
        assertThat(login(email, "wrong-password1").getStatusCode().value()).isEqualTo(401);
        assertThat(login(email, "wrong-password1").getStatusCode().value()).isEqualTo(401);
        assertThat(login(email, "secret123").getStatusCode().value()).isEqualTo(200);
        assertThat(authAccountRepository.findById(userId).orElseThrow().getLockState())
                .isEqualTo(LockState.NONE);
    }

    private ResponseEntity<String> login(String email, String password) {
        return rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, password, DeviceBindingRequestFixture.webDevice()),
                String.class);
    }
}
