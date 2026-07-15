package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.DormantReleaseChallengeRequest;
import com.example.auth.app.api.presentation.v1.DormantReleaseChallengeResponse;
import com.example.auth.app.api.presentation.v1.DormantReleaseVerifyRequest;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.auth.service.AuthAccountModifier;
import com.example.auth.domain.user.repository.UserRepository;
import com.example.auth.external.notification.MockVerificationCodeSender;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 휴면 해제 E2E: 휴면 계정 로그인 차단(자격 검증 성공자에게만 403 안내), OTP 재인증으로만 해제,
 * 해제 직후 정상 로그인을 실 PostgreSQL·Redis에 대해 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class DormantReleaseE2EIT {

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
    private AuthAccountModifier authAccountModifier;

    @Autowired
    private MockVerificationCodeSender verificationCodeSender;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void dormantAccountRequiresOtpReleaseBeforeLogin() {
        String email = "sleeper@example.com";
        UUID userId = provisioning.provision(email, "secret123");
        makeDormant(userId);

        // 스냅샷이 DORMANT인 동안 OTP 없이 로그인 불가 — 올바른 자격이어도 세션이 발급되지 않는다.
        ResponseEntity<String> blocked = loginRaw(email, "secret123");
        assertThat(blocked.getStatusCode().value()).isEqualTo(403);
        assertThat(requireNonNull(blocked.getBody())).contains("AUTH_ACCOUNT_DORMANT");

        // 잘못된 자격에는 휴면 사실을 노출하지 않는다(열거 저항 — 단일 401).
        assertThat(loginRaw(email, "wrongpass1").getStatusCode().value()).isEqualTo(401);

        // 잘못된 자격으로는 해제 챌린지도 시작할 수 없다.
        ResponseEntity<String> badChallenge = rest.postForEntity(
                "/auth/dormant-release/challenge",
                new DormantReleaseChallengeRequest(email, "wrongpass1"),
                String.class);
        assertThat(badChallenge.getStatusCode().value()).isEqualTo(401);

        // 자격 검증 성공 → 연락용 이메일로 OTP 발송(수신처는 마스킹 표시).
        ResponseEntity<DormantReleaseChallengeResponse> challenge = rest.postForEntity(
                "/auth/dormant-release/challenge",
                new DormantReleaseChallengeRequest(email, "secret123"),
                DormantReleaseChallengeResponse.class);
        assertThat(challenge.getStatusCode().value()).isEqualTo(200);
        DormantReleaseChallengeResponse issued = requireNonNull(challenge.getBody());
        assertThat(issued.maskedTarget()).isEqualTo("sl***@example.com");

        // 잘못된 코드 → 400, 여전히 로그인 불가(OTP 없는 해제 경로 없음).
        ResponseEntity<String> wrongCode = rest.postForEntity(
                "/auth/dormant-release/verify",
                new DormantReleaseVerifyRequest(issued.challengeId(), "000000"),
                String.class);
        assertThat(wrongCode.getStatusCode().value()).isEqualTo(400);
        assertThat(loginRaw(email, "secret123").getStatusCode().value()).isEqualTo(403);

        // 올바른 코드 → 해제 → 직후 정상 로그인.
        String code = requireNonNull(verificationCodeSender.lastContent(email));
        ResponseEntity<Void> released = rest.postForEntity(
                "/auth/dormant-release/verify",
                new DormantReleaseVerifyRequest(issued.challengeId(), code),
                Void.class);
        assertThat(released.getStatusCode().value()).isEqualTo(204);

        ResponseEntity<TokenResponse> login = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, "secret123", DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(userRepository.findById(userId).orElseThrow().getStatus().name())
                .isEqualTo("ACTIVE");
    }

    private void makeDormant(UUID userId) {
        // 배치 전환을 재현한다 — 유저 권위 전이 + 인증 스냅샷 반영(배치 이벤트 소비 경로는 배치 IT가 검증).
        Long version = requireNonNull(new TransactionTemplate(transactionManager).execute(status -> {
            var user = userRepository.findById(userId).orElseThrow();
            user.makeDormant(Instant.now());
            return user.getStatusVersion();
        }));
        authAccountModifier.applyUserStatus(
                userId, com.example.auth.domain.auth.entity.LifecycleStatus.DORMANT, version);
    }

    private ResponseEntity<String> loginRaw(String email, String password) {
        return rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, password, DeviceBindingRequestFixture.webDevice()),
                String.class);
    }
}
