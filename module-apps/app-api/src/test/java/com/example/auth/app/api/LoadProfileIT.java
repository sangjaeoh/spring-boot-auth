package com.example.auth.app.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.RefreshRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
 * 부하 프로파일 하네스(옵트인 — {@code -DloadProfile=true}에서만 실행, 기본 빌드는 스킵).
 *
 * <p>핫패스 4개(로그인 Argon2 경로·보호 API 세션 검증·리프레시 회전 Lua·동일 계정 동시 로그인 상한 Lua 경합)의
 * p50/p95/p99와 처리량, 세션당 Redis 메모리를 실측해 마크다운 리포트로 출력한다. 결과 정본은
 * docs/ops/load-report.md에 기록한다(로컬 실측 — 절대값은 하드웨어 종속, 예산 검증·상대 비교용).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
@EnabledIfSystemProperty(named = "loadProfile", matches = "true")
class LoadProfileIT {

    private static final int USERS = 40;
    private static final int LOGIN_CONCURRENCY = 8;
    private static final int LOGIN_REQUESTS = 200;
    private static final int HOTPATH_CONCURRENCY = 64;
    private static final int HOTPATH_REQUESTS = 5_000;
    private static final int REFRESH_CHAINS = 40;
    private static final int REFRESH_PER_CHAIN = 25;
    private static final int CONTENTION_CONCURRENCY = 16;
    private static final int CONTENTION_REQUESTS = 100;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // 엔진 용량을 재는 프로파일이라 정책 리밋을 상향한다(prod 실효 처리량은 레이트리밋이 지배 —
        // 리포트에 명시). 세션 상한·잠금 등 나머지 정책은 기본값 그대로 둔다.
        registry.add("auth.rate-limit.login.ip-limit", () -> "1000000");
        registry.add("auth.rate-limit.login.account-limit", () -> "1000000");
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AccountProvisioningFacade provisioning;

    @Test
    void profileHotPaths() throws Exception {
        for (int i = 0; i < USERS; i++) {
            provisioning.provision(email(i), "secret123");
        }
        // 워밍업(JIT·커넥션 풀·DEK 캐시).
        runScenario(2, 50, i -> login(i % USERS));

        long redisBaseline = redisUsedMemory();

        Result loginResult = runScenario(LOGIN_CONCURRENCY, LOGIN_REQUESTS, i -> login(i % USERS));

        List<TokenResponse> sessions = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            sessions.add(login(i));
        }
        long redisWithSessions = redisUsedMemory();

        Result hotPathResult = runScenario(
                HOTPATH_CONCURRENCY,
                HOTPATH_REQUESTS,
                i -> me(sessions.get(i % sessions.size()).accessToken()));

        Result refreshResult = profileRefreshChains();

        Result contentionResult = runScenario(CONTENTION_CONCURRENCY, CONTENTION_REQUESTS, i -> login(0));

        long perSession = Math.max(0, redisWithSessions - redisBaseline) / USERS;
        String report = """
                | 시나리오 | 요청 수 | 동시성 | 오류 | p50(ms) | p95(ms) | p99(ms) | max(ms) | 처리량(req/s) |
                | --- | --- | --- | --- | --- | --- | --- | --- | --- |
                %s
                %s
                %s
                %s

                세션당 Redis 메모리(세션 %d개 실측): ~%d bytes
                """.formatted(
                        loginResult.row("로그인(Argon2)", LOGIN_CONCURRENCY),
                        hotPathResult.row("보호 API(세션 검증)", HOTPATH_CONCURRENCY),
                        refreshResult.row("리프레시 회전(Lua)", REFRESH_CHAINS),
                        contentionResult.row("동일 계정 로그인 경합", CONTENTION_CONCURRENCY),
                        USERS,
                        perSession);
        System.out.println("=== LOAD PROFILE REPORT ===");
        System.out.println(report);
        System.out.println("=== END REPORT ===");

        // 경합 시나리오는 과부하 프로브라 오류 수가 곧 데이터다(표에 기록). 정상 시나리오만 무오류 게이트.
        assertThat(loginResult.errors).isZero();
        assertThat(hotPathResult.errors).isZero();
        assertThat(refreshResult.errors).isZero();
    }

    private Result profileRefreshChains() throws InterruptedException {
        List<TokenResponse> chains = new ArrayList<>();
        for (int i = 0; i < REFRESH_CHAINS; i++) {
            chains.add(login(i % USERS));
        }
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(REFRESH_CHAINS);
        long started = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (TokenResponse chain : chains) {
                var _ = executor.submit(() -> {
                    TokenResponse current = chain;
                    for (int i = 0; i < REFRESH_PER_CHAIN; i++) {
                        long t0 = System.nanoTime();
                        ResponseEntity<TokenResponse> response = rest.postForEntity(
                                "/auth/token/refresh", new RefreshRequest(current.refreshToken()), TokenResponse.class);
                        latencies.add(System.nanoTime() - t0);
                        TokenResponse body = response.getBody();
                        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                            errors.incrementAndGet();
                            break;
                        }
                        current = body;
                    }
                    done.countDown();
                });
            }
            done.await();
        }
        return new Result(latencies, errors.get(), REFRESH_CHAINS * REFRESH_PER_CHAIN, System.nanoTime() - started);
    }

    private Result runScenario(int concurrency, int requests, ThrowingCall call) throws InterruptedException {
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger next = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(concurrency);
        long started = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int worker = 0; worker < concurrency; worker++) {
                var _ = executor.submit(() -> {
                    int i;
                    while ((i = next.getAndIncrement()) < requests) {
                        long t0 = System.nanoTime();
                        try {
                            call.run(i);
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        }
                        latencies.add(System.nanoTime() - t0);
                    }
                    done.countDown();
                });
            }
            done.await();
        }
        return new Result(latencies, errors.get(), requests, System.nanoTime() - started);
    }

    private TokenResponse login(int userIndex) {
        ResponseEntity<TokenResponse> response = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email(userIndex), "secret123", DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
        TokenResponse body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            throw new IllegalStateException("로그인 실패: " + response.getStatusCode());
        }
        return body;
    }

    private void me(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<String> response =
                rest.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("세션 검증 실패: " + response.getStatusCode());
        }
    }

    private long redisUsedMemory() throws Exception {
        String info = redis.execInContainer("redis-cli", "INFO", "memory").getStdout();
        for (String line : info.split("\r?\n", -1)) {
            if (line.startsWith("used_memory:")) {
                return Long.parseLong(line.substring("used_memory:".length()));
            }
        }
        throw new IllegalStateException("used_memory를 찾지 못했다");
    }

    private static String email(int index) {
        return "load-" + index + "@example.com";
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run(int index) throws Exception;
    }

    private record Result(ConcurrentLinkedQueue<Long> latencies, int errors, int requests, long elapsedNanos) {

        String row(String name, int concurrency) {
            List<Long> sorted = latencies.stream().sorted().toList();
            double throughput = requests / (elapsedNanos / 1_000_000_000.0);
            return "| %s | %d | %d | %d | %.1f | %.1f | %.1f | %.1f | %.0f |"
                    .formatted(
                            name,
                            requests,
                            concurrency,
                            errors,
                            millis(sorted, 0.50),
                            millis(sorted, 0.95),
                            millis(sorted, 0.99),
                            sorted.isEmpty() ? 0 : sorted.getLast() / 1_000_000.0,
                            throughput);
        }

        private static double millis(List<Long> sorted, double quantile) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int index = (int) Math.min(sorted.size() - 1, Math.ceil(quantile * sorted.size()) - 1);
            return sorted.get(Math.max(0, index)) / 1_000_000.0;
        }
    }
}
