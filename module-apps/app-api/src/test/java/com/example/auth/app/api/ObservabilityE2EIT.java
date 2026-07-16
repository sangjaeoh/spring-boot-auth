package com.example.auth.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 관측성 E2E: 분리된 관리 포트에서 health(프로브 포함)·Prometheus 지표가 무인증으로 응답하고, 보안 탐지
 * 카운터와 URI별 지연 히스토그램이 게시되며, 서비스 포트에는 관리 엔드포인트가 매핑되지 않음을 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ObservabilityE2EIT {

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
    private Environment environment;

    @Autowired
    private AccountProvisioningFacade provisioning;

    @Test
    void healthAndPrometheusAreServedOnManagementPortOnly() {
        provisioning.provision("observability@example.com", "secret123");
        ResponseEntity<TokenResponse> login = rest.postForEntity(
                "/auth/login",
                new LoginRequest("observability@example.com", "secret123", DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        String management = "http://localhost:" + environment.getProperty("local.management.port");

        ResponseEntity<String> health = rest.getForEntity(management + "/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
        assertThat(rest.getForEntity(management + "/actuator/health/liveness", String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity(management + "/actuator/health/readiness", String.class)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> prometheus = rest.getForEntity(management + "/actuator/prometheus", String.class);
        assertThat(prometheus.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 보안 탐지 카운터(등록 시점 게시)와 로그인 지연 히스토그램 버킷(p99 원천)이 함께 게시된다.
        assertThat(prometheus.getBody())
                .contains("auth_login_success_total")
                .contains("auth_refresh_reuse_detected_total")
                .contains("auth_account_locked_total")
                .contains("http_server_requests_seconds_bucket");

        // 서비스 포트에는 관리 엔드포인트가 매핑되지 않는다(내부망 격리 전제).
        ResponseEntity<String> onServicePort = rest.getForEntity("/actuator/health", String.class);
        assertThat(onServicePort.getStatusCode().is2xxSuccessful()).isFalse();
    }
}
