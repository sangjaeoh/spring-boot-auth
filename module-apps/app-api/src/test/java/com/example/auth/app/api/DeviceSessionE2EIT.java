package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.DevicePushPermissionRequest;
import com.example.auth.app.api.presentation.v1.DevicePushTokenRequest;
import com.example.auth.app.api.presentation.v1.DeviceRegisterRequest;
import com.example.auth.app.api.presentation.v1.DeviceRegisterResponse;
import com.example.auth.app.api.presentation.v1.DeviceResponse;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.SessionResponse;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.DevicePlatform;
import com.example.auth.domain.auth.entity.PushPlatform;
import com.example.auth.domain.auth.event.ConcurrentLimitExceeded;
import com.example.auth.domain.auth.event.ForceLogoutRequested;
import com.example.auth.domain.auth.event.NewDeviceDetected;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
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
 * 기기·세션 통제 E2E: 동시 세션 상한(최오래 축출 즉시 401), 원격/강제 로그아웃 즉시 401, 기기 삭제 →
 * 그 기기 세션 401, 신규 기기 인식·이벤트 발행을 실 PostgreSQL·Redis에 대해 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class DeviceSessionE2EIT {

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

    @TestConfiguration
    static class RecordingConsumers {

        @Bean
        RecordingConsumer<NewDeviceDetected> newDeviceRecorder() {
            return new RecordingConsumer<>("test.new-device-recorder", NewDeviceDetected.class);
        }

        @Bean
        RecordingConsumer<ConcurrentLimitExceeded> limitExceededRecorder() {
            return new RecordingConsumer<>("test.limit-exceeded-recorder", ConcurrentLimitExceeded.class);
        }
    }

    static final class RecordingConsumer<E extends com.example.auth.common.messaging.IntegrationEvent>
            implements IntegrationEventConsumer<E> {

        private final String consumerId;
        private final Class<E> eventType;
        final List<E> consumed = new CopyOnWriteArrayList<>();

        RecordingConsumer(String consumerId, Class<E> eventType) {
            this.consumerId = consumerId;
            this.eventType = eventType;
        }

        @Override
        public String consumerId() {
            return consumerId;
        }

        @Override
        public Class<E> eventType() {
            return eventType;
        }

        @Override
        public void consume(E event) {
            consumed.add(event);
        }
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AccountProvisioningFacade provisioning;

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private RecordingConsumer<NewDeviceDetected> newDeviceRecorder;

    @Autowired
    private RecordingConsumer<ConcurrentLimitExceeded> limitExceededRecorder;

    @BeforeEach
    void resetRecorders() {
        newDeviceRecorder.consumed.clear();
        limitExceededRecorder.consumed.clear();
    }

    @Test
    void exceedingConcurrentLimitEvictsOldestSessionImmediately() {
        UUID userId = provisioning.provision("limit@example.com", "secret123");

        TokenResponse first = login("limit@example.com", "fp-limit-1");
        TokenResponse second = login("limit@example.com", "fp-limit-2");
        TokenResponse third = login("limit@example.com", "fp-limit-3");
        assertThat(limitExceededRecorder.consumed).isEmpty();

        // 상한 3 초과의 4번째 로그인 → 최오래(first) 세션 즉시 401 + 초과 이벤트 발행.
        TokenResponse fourth = login("limit@example.com", "fp-limit-4");

        assertThat(meStatus(first.accessToken())).isEqualTo(401);
        assertThat(meStatus(second.accessToken())).isEqualTo(200);
        assertThat(meStatus(third.accessToken())).isEqualTo(200);
        assertThat(meStatus(fourth.accessToken())).isEqualTo(200);
        assertThat(limitExceededRecorder.consumed).hasSize(1);
        assertThat(limitExceededRecorder.consumed.get(0).userId()).isEqualTo(userId);
        assertThat(limitExceededRecorder.consumed.get(0).evictedSessionIds()).hasSize(1);
    }

    @Test
    void remoteLogoutRevokesTargetSessionImmediately() {
        provisioning.provision("remote@example.com", "secret123");
        TokenResponse mine = login("remote@example.com", "fp-remote-mine");
        TokenResponse other = login("remote@example.com", "fp-remote-other");

        // 상대 세션의 sessionId는 상대 세션의 목록에서 current로 식별한다.
        UUID otherSessionId = currentSessionId(other.accessToken());

        ResponseEntity<Void> revoked = rest.exchange(
                "/auth/sessions/" + otherSessionId, HttpMethod.DELETE, bearer(mine.accessToken()), Void.class);
        assertThat(revoked.getStatusCode().value()).isEqualTo(204);

        assertThat(meStatus(other.accessToken())).isEqualTo(401);
        assertThat(meStatus(mine.accessToken())).isEqualTo(200);

        // 이미 종료된 세션의 재종료는 404.
        ResponseEntity<String> replayed = rest.exchange(
                "/auth/sessions/" + otherSessionId, HttpMethod.DELETE, bearer(mine.accessToken()), String.class);
        assertThat(replayed.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void revokeOthersKeepsOnlyCurrentSession() {
        provisioning.provision("others@example.com", "secret123");
        TokenResponse first = login("others@example.com", "fp-others-1");
        TokenResponse second = login("others@example.com", "fp-others-2");
        TokenResponse current = login("others@example.com", "fp-others-3");

        ResponseEntity<Void> response =
                rest.exchange("/auth/sessions", HttpMethod.DELETE, bearer(current.accessToken()), Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        assertThat(meStatus(first.accessToken())).isEqualTo(401);
        assertThat(meStatus(second.accessToken())).isEqualTo(401);
        assertThat(meStatus(current.accessToken())).isEqualTo(200);

        List<SessionResponse> sessions = sessions(current.accessToken());
        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).current()).isTrue();
    }

    @Test
    void firstLoginRegistersDeviceAndPublishesNewDeviceDetectedOnce() {
        UUID userId = provisioning.provision("newdev@example.com", "secret123");

        TokenResponse tokens = login("newdev@example.com", "fp-newdev");
        assertThat(newDeviceRecorder.consumed).hasSize(1);
        assertThat(newDeviceRecorder.consumed.get(0).userId()).isEqualTo(userId);

        // 같은 지문의 재로그인은 기존 기기로 인식된다 — 신규 감지 이벤트가 없고 기기 행이 늘지 않는다.
        login("newdev@example.com", "fp-newdev");
        assertThat(newDeviceRecorder.consumed).hasSize(1);

        List<DeviceResponse> devices = devices(tokens.accessToken());
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).deviceName()).isEqualTo("테스트 브라우저");
        assertThat(devices.get(0).platform()).isEqualTo(DevicePlatform.WEB);
        assertThat(devices.get(0).lastIp()).isNotNull();

        // 세션 목록이 기기명을 조인해 노출하고 현재 세션을 표시한다.
        List<SessionResponse> sessions = sessions(tokens.accessToken());
        assertThat(sessions).hasSize(2);
        assertThat(sessions)
                .allSatisfy(session -> assertThat(session.deviceName()).isEqualTo("테스트 브라우저"));
        assertThat(sessions).filteredOn(SessionResponse::current).hasSize(1);
    }

    @Test
    void deletingDeviceRevokesItsSessionsImmediately() {
        provisioning.provision("deldev@example.com", "secret123");
        TokenResponse mine = login("deldev@example.com", "fp-del-mine");
        TokenResponse doomed = login("deldev@example.com", "fp-del-doomed");
        UUID doomedDeviceId = requireNonNull(sessions(doomed.accessToken()).stream()
                .filter(SessionResponse::current)
                .findFirst()
                .orElseThrow()
                .deviceId());

        ResponseEntity<Void> deleted = rest.exchange(
                "/auth/devices/" + doomedDeviceId, HttpMethod.DELETE, bearer(mine.accessToken()), Void.class);
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);

        // 삭제된 기기의 세션은 즉시 401, 내 세션은 유지, 기기 목록에서 제거.
        assertThat(meStatus(doomed.accessToken())).isEqualTo(401);
        assertThat(meStatus(mine.accessToken())).isEqualTo(200);
        assertThat(devices(mine.accessToken())).hasSize(1);
    }

    @Test
    void forceLogoutRevokesAllSessionsImmediately() {
        UUID userId = provisioning.provision("forced@example.com", "secret123");
        TokenResponse first = login("forced@example.com", "fp-forced-1");
        TokenResponse second = login("forced@example.com", "fp-forced-2");

        // 관리자 발행측(후속 단계)을 대신해 통합 이벤트를 직접 발행한다 — 소비 계약을 검증한다.
        messagePublisher.publish(new ForceLogoutRequested(userId, Instant.now()));

        assertThat(meStatus(first.accessToken())).isEqualTo(401);
        assertThat(meStatus(second.accessToken())).isEqualTo(401);
    }

    @Test
    void managesDeviceRegistrationTrustAndPushTarget() {
        provisioning.provision("manage@example.com", "secret123");
        TokenResponse tokens = login("manage@example.com", "fp-manage-login");

        // 명시 등록 + 중복 지문 409.
        ResponseEntity<DeviceRegisterResponse> registered = rest.exchange(
                "/auth/devices",
                HttpMethod.POST,
                bearer(tokens.accessToken(), new DeviceRegisterRequest("fp-manage-extra", "보조 기기", DevicePlatform.IOS)),
                DeviceRegisterResponse.class);
        assertThat(registered.getStatusCode().value()).isEqualTo(201);
        UUID deviceId = requireNonNull(registered.getBody()).deviceId();
        ResponseEntity<String> duplicated = rest.exchange(
                "/auth/devices",
                HttpMethod.POST,
                bearer(tokens.accessToken(), new DeviceRegisterRequest("fp-manage-extra", "보조 기기", DevicePlatform.IOS)),
                String.class);
        assertThat(duplicated.getStatusCode().value()).isEqualTo(409);

        // 신뢰 + 푸시 토큰/권한 반영이 기기 목록에 나타난다.
        assertThat(rest.exchange(
                                "/auth/devices/" + deviceId + "/trust",
                                HttpMethod.PUT,
                                bearer(tokens.accessToken()),
                                Void.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(204);
        assertThat(rest.exchange(
                                "/auth/devices/" + deviceId + "/push-token",
                                HttpMethod.PUT,
                                bearer(
                                        tokens.accessToken(),
                                        new DevicePushTokenRequest("push-token-1", PushPlatform.APNS)),
                                Void.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(204);
        assertThat(rest.exchange(
                                "/auth/devices/" + deviceId + "/push-permission",
                                HttpMethod.PUT,
                                bearer(tokens.accessToken(), new DevicePushPermissionRequest(true)),
                                Void.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(204);

        DeviceResponse extra = devices(tokens.accessToken()).stream()
                .filter(device -> device.deviceId().equals(deviceId))
                .findFirst()
                .orElseThrow();
        assertThat(extra.trusted()).isTrue();
        assertThat(extra.pushEnabled()).isTrue();

        // 타인 기기 접근 차단은 (id, userId) 조회가 겸한다 — 미존재 기기와 동일한 404.
        assertThat(rest.exchange(
                                "/auth/devices/" + UUID.randomUUID() + "/trust",
                                HttpMethod.PUT,
                                bearer(tokens.accessToken()),
                                String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
    }

    private TokenResponse login(String email, String fingerprint) {
        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, "secret123", DeviceBindingRequestFixture.webDevice(fingerprint)),
                TokenResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private int meStatus(String accessToken) {
        return rest.exchange("/auth/me", HttpMethod.GET, bearer(accessToken), String.class)
                .getStatusCode()
                .value();
    }

    private List<SessionResponse> sessions(String accessToken) {
        ResponseEntity<List<SessionResponse>> response = rest.exchange(
                "/auth/sessions",
                HttpMethod.GET,
                bearer(accessToken),
                new ParameterizedTypeReference<List<SessionResponse>>() {});
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private List<DeviceResponse> devices(String accessToken) {
        ResponseEntity<List<DeviceResponse>> response = rest.exchange(
                "/auth/devices",
                HttpMethod.GET,
                bearer(accessToken),
                new ParameterizedTypeReference<List<DeviceResponse>>() {});
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private UUID currentSessionId(String accessToken) {
        return sessions(accessToken).stream()
                .filter(SessionResponse::current)
                .findFirst()
                .orElseThrow()
                .sessionId();
    }

    private static HttpEntity<Void> bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(headers);
    }

    private static <T> HttpEntity<T> bearer(String accessToken, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return new HttpEntity<>(body, headers);
    }
}
