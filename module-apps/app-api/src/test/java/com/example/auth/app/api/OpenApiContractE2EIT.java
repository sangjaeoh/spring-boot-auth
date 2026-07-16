package com.example.auth.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * OpenAPI 계약 E2E: 스펙이 무인증으로 게시되고 v1 표면(공개·보호 라우트)과 계약 버전을 담는지 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class OpenApiContractE2EIT {

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

    @Test
    void publishesOpenApiContractCoveringV1Surface() {
        ResponseEntity<String> spec = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(spec.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(spec.getBody())
                .contains("\"openapi\"")
                .contains("\"version\":\"v1\"")
                // 공개(로그인)·보호(내 세션)·well-known(JWKS) 표면이 계약에 드러난다.
                .contains("\"/auth/login\"")
                .contains("\"/auth/me\"")
                .contains("\"/.well-known/jwks.json\"");
    }
}
