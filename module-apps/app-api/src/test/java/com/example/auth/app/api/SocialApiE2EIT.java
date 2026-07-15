package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.api.facade.AccountProvisioningFacade;
import com.example.auth.app.api.presentation.v1.ConsentsRequest;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.IdentityVerifyRequest;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.MeResponse;
import com.example.auth.app.api.presentation.v1.SocialConnectRequest;
import com.example.auth.app.api.presentation.v1.SocialLoginRequest;
import com.example.auth.app.api.presentation.v1.SocialLoginResponse;
import com.example.auth.app.api.presentation.v1.SocialRegistrationCompleteRequest;
import com.example.auth.app.api.presentation.v1.SocialRegistrationCompleteResponse;
import com.example.auth.app.api.presentation.v1.SocialRegistrationResponse;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.auth.entity.SocialProvider;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.TermsType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 소셜 로그인 E2E(Mock IdP·오프라인): 신규 소셜 가입(SOCIAL 온보딩 → 원자 커밋 → 즉시 세션), 기존 연동
 * 재로그인, (provider, subject) 중복 연결 거부, 마지막 수단 해제 거부, 동시 해제 직렬화(둘 중 하나만
 * 성공), 애플 릴레이 이메일의 contactEmail seed를 실 PostgreSQL·Redis로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class SocialApiE2EIT {

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
    private AccountProvisioningFacade accountProvisioningFacade;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void completesNewSocialSignupThenReloginIssuesSessionImmediately() {
        String email = "social-happy@example.com";
        String idToken = "mock:kakao-happy:" + email;

        // 미연동 첫 로그인은 온보딩 유도로 응답한다(계정·연동 미생성).
        SocialLoginResponse first = socialLogin(SocialProvider.KAKAO, idToken);
        assertThat(first.status()).isEqualTo(SocialLoginResponse.Status.REGISTRATION_REQUIRED);
        assertThat(first.token()).isNull();
        SocialRegistrationResponse registration = requireNonNull(first.registration());

        verifyIdentityStep(registration.registrationId(), registration.onboardingToken(), "김소셜", "+821055550001");
        submitConsents(registration.registrationId(), registration.onboardingToken());

        ResponseEntity<SocialRegistrationCompleteResponse> completed = rest.postForEntity(
                "/auth/social/registration/complete",
                new SocialRegistrationCompleteRequest(
                        registration.registrationId(),
                        registration.onboardingToken(),
                        DeviceBindingRequestFixture.webDevice()),
                SocialRegistrationCompleteResponse.class);
        assertThat(completed.getStatusCode().value()).isEqualTo(201);
        SocialRegistrationCompleteResponse body = requireNonNull(completed.getBody());

        // 가입 완료가 즉시 로그인 세션을 발급한다.
        assertThat(me(body.accessToken()).userId()).isEqualTo(body.userId());

        // 원자 커밋 결과: 소셜 연동 1건 + 비밀번호 없는 계정(PasswordCredential 0..1).
        assertThat(count(
                        "select count(*) from auth.social_connection where user_id = ? and provider = 'KAKAO'"
                                + " and provider_user_id = 'kakao-happy'",
                        body.userId()))
                .isEqualTo(1);
        assertThat(count("select count(*) from auth.password_credential where user_id = ?", body.userId()))
                .isZero();
        assertThat(count(
                        "select count(*) from auth.auth_account where user_id = ? and login_email = ?",
                        body.userId(),
                        email))
                .isEqualTo(1);

        // 소비된 세션 재커밋은 404.
        ResponseEntity<String> replayed = rest.postForEntity(
                "/auth/social/registration/complete",
                new SocialRegistrationCompleteRequest(
                        registration.registrationId(),
                        registration.onboardingToken(),
                        DeviceBindingRequestFixture.webDevice()),
                String.class);
        assertThat(replayed.getStatusCode().value()).isEqualTo(404);

        // 기존 연동 재로그인은 온보딩 없이 즉시 세션이다.
        SocialLoginResponse relogin = socialLogin(SocialProvider.KAKAO, idToken);
        assertThat(relogin.status()).isEqualTo(SocialLoginResponse.Status.LOGGED_IN);
        TokenResponse tokens = requireNonNull(relogin.token());
        assertThat(me(tokens.accessToken()).userId()).isEqualTo(body.userId());
    }

    @Test
    void rejectsForgedMockToken() {
        ResponseEntity<String> response = rest.postForEntity(
                "/auth/social/login",
                new SocialLoginRequest(
                        SocialProvider.KAKAO, "not-a-mock-token", DeviceBindingRequestFixture.webDevice()),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void rejectsConnectingSubjectAlreadyLinkedToAnotherAccount() {
        String duplicateSubjectToken = "mock:dup-subject:dup-owner@example.com";
        signupSocial(SocialProvider.KAKAO, duplicateSubjectToken, "김중복", "+821055550002");

        SocialRegistrationCompleteResponse other =
                signupSocial(SocialProvider.GOOGLE, "mock:other-google:other@example.com", "김타인", "+821055550003");

        // 타 계정에 이미 연결된 (provider, subject) 연동 시도 → 409.
        ResponseEntity<String> conflict = rest.exchange(
                "/auth/social/connections",
                HttpMethod.POST,
                new HttpEntity<>(
                        new SocialConnectRequest(SocialProvider.KAKAO, duplicateSubjectToken),
                        bearer(other.accessToken())),
                String.class);
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);

        // 이미 연동한 provider의 중복 연동 시도 → 409.
        ResponseEntity<String> providerDup = rest.exchange(
                "/auth/social/connections",
                HttpMethod.POST,
                new HttpEntity<>(
                        new SocialConnectRequest(SocialProvider.GOOGLE, "mock:another-google:x@example.com"),
                        bearer(other.accessToken())),
                String.class);
        assertThat(providerDup.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void seedsAppleRelayEmailAsContactEmailAndRejectsLastMethodDisconnect() {
        String relayEmail = "abc123@privaterelay.appleid.com";
        SocialRegistrationCompleteResponse signedUp =
                signupSocial(SocialProvider.APPLE, "mock:apple-relay:" + relayEmail, "김애플", "+821055550004");

        // 릴레이 이메일이 연동에 릴레이로 표시되고 회원 contactEmail의 seed가 된다.
        Boolean privateRelay = jdbc.queryForObject(
                "select is_private_relay from auth.social_connection where user_id = ?",
                Boolean.class,
                signedUp.userId());
        assertThat(privateRelay).isTrue();
        String contactEmail = jdbc.queryForObject(
                "select contact_email from usr.users where id = ?", String.class, signedUp.userId());
        assertThat(contactEmail).isEqualTo(relayEmail);

        // 유일한 로그인 수단(애플) 해제 → 409, 연동은 그대로 남는다.
        ResponseEntity<String> rejected = rest.exchange(
                "/auth/social/connections/APPLE",
                HttpMethod.DELETE,
                new HttpEntity<>(bearer(signedUp.accessToken())),
                String.class);
        assertThat(rejected.getStatusCode().value()).isEqualTo(409);
        assertThat(count("select count(*) from auth.social_connection where user_id = ?", signedUp.userId()))
                .isEqualTo(1);
    }

    @Test
    void serializesConcurrentDisconnectsSoExactlyOneSucceeds() throws Exception {
        SocialRegistrationCompleteResponse signedUp =
                signupSocial(SocialProvider.KAKAO, "mock:race-kakao:race@example.com", "김경합", "+821055550005");
        connect(signedUp.accessToken(), SocialProvider.GOOGLE, "mock:race-google:race@example.com");
        assertThat(count("select count(*) from auth.social_connection where user_id = ?", signedUp.userId()))
                .isEqualTo(2);

        // 두 수단(카카오·구글)을 동시에 해제 — 직렬화 없이는 둘 다 "2개 중 하나"를 관측해 0이 된다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(1);
            List<Future<Integer>> results = List.of(
                    executor.submit(() -> {
                        ready.await();
                        return disconnectStatus(signedUp.accessToken(), "KAKAO");
                    }),
                    executor.submit(() -> {
                        ready.await();
                        return disconnectStatus(signedUp.accessToken(), "GOOGLE");
                    }));
            ready.countDown();
            List<Integer> statuses =
                    List.of(results.get(0).get(), results.get(1).get());

            assertThat(statuses).containsExactlyInAnyOrder(204, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(count("select count(*) from auth.social_connection where user_id = ?", signedUp.userId()))
                .isEqualTo(1);
    }

    @Test
    void disconnectsSocialWhenPasswordCredentialRemains() {
        String email = "local-with-social@example.com";
        accountProvisioningFacade.provision(email, "secret123");
        ResponseEntity<TokenResponse> loggedIn = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, "secret123", DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
        String accessToken = requireNonNull(loggedIn.getBody()).accessToken();

        connect(accessToken, SocialProvider.NAVER, "mock:naver-local:naver-local@example.com");

        // 비밀번호 수단이 남으므로 소셜 해제는 허용된다.
        assertThat(disconnectStatus(accessToken, "NAVER")).isEqualTo(204);

        // 이미 해제된 연동의 재해제 → 404.
        assertThat(disconnectStatus(accessToken, "NAVER")).isEqualTo(404);
    }

    private SocialLoginResponse socialLogin(SocialProvider provider, String idToken) {
        ResponseEntity<SocialLoginResponse> response = rest.postForEntity(
                "/auth/social/login",
                new SocialLoginRequest(provider, idToken, DeviceBindingRequestFixture.webDevice()),
                SocialLoginResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private SocialRegistrationCompleteResponse signupSocial(
            SocialProvider provider, String idToken, String name, String phone) {
        SocialLoginResponse login = socialLogin(provider, idToken);
        assertThat(login.status()).isEqualTo(SocialLoginResponse.Status.REGISTRATION_REQUIRED);
        SocialRegistrationResponse registration = requireNonNull(login.registration());
        verifyIdentityStep(registration.registrationId(), registration.onboardingToken(), name, phone);
        submitConsents(registration.registrationId(), registration.onboardingToken());
        ResponseEntity<SocialRegistrationCompleteResponse> completed = rest.postForEntity(
                "/auth/social/registration/complete",
                new SocialRegistrationCompleteRequest(
                        registration.registrationId(),
                        registration.onboardingToken(),
                        DeviceBindingRequestFixture.webDevice()),
                SocialRegistrationCompleteResponse.class);
        assertThat(completed.getStatusCode().value()).isEqualTo(201);
        return requireNonNull(completed.getBody());
    }

    private void verifyIdentityStep(UUID registrationId, String onboardingToken, String name, String phone) {
        ResponseEntity<Void> response = rest.postForEntity(
                "/auth/registration/identity/verify",
                new IdentityVerifyRequest(
                        registrationId,
                        onboardingToken,
                        name,
                        LocalDate.of(1992, 5, 20),
                        Gender.FEMALE,
                        Carrier.KT,
                        phone),
                Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private void submitConsents(UUID registrationId, String onboardingToken) {
        ResponseEntity<Void> response = rest.postForEntity(
                "/auth/registration/consents",
                new ConsentsRequest(
                        registrationId,
                        onboardingToken,
                        List.of(
                                new ConsentsRequest.ConsentItem(TermsType.SERVICE, 1),
                                new ConsentsRequest.ConsentItem(TermsType.PRIVACY_REQUIRED, 1),
                                new ConsentsRequest.ConsentItem(TermsType.AGE14, 1))),
                Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private void connect(String accessToken, SocialProvider provider, String idToken) {
        ResponseEntity<Void> response = rest.exchange(
                "/auth/social/connections",
                HttpMethod.POST,
                new HttpEntity<>(new SocialConnectRequest(provider, idToken), bearer(accessToken)),
                Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private int disconnectStatus(String accessToken, String provider) {
        ResponseEntity<String> response = rest.exchange(
                "/auth/social/connections/" + provider,
                HttpMethod.DELETE,
                new HttpEntity<>(bearer(accessToken)),
                String.class);
        return response.getStatusCode().value();
    }

    private MeResponse me(String accessToken) {
        ResponseEntity<MeResponse> response =
                rest.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(bearer(accessToken)), MeResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private HttpHeaders bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
