package com.example.auth.app.api;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.app.api.presentation.v1.ChallengeResponse;
import com.example.auth.app.api.presentation.v1.ConsentsRequest;
import com.example.auth.app.api.presentation.v1.DeviceBindingRequestFixture;
import com.example.auth.app.api.presentation.v1.EmailChallengeRequest;
import com.example.auth.app.api.presentation.v1.IdentityVerifyRequest;
import com.example.auth.app.api.presentation.v1.LoginRequest;
import com.example.auth.app.api.presentation.v1.MeResponse;
import com.example.auth.app.api.presentation.v1.PhoneChallengeRequest;
import com.example.auth.app.api.presentation.v1.RegistrationCodeVerifyRequest;
import com.example.auth.app.api.presentation.v1.RegistrationCompleteRequest;
import com.example.auth.app.api.presentation.v1.RegistrationCompleteResponse;
import com.example.auth.app.api.presentation.v1.RegistrationStartRequest;
import com.example.auth.app.api.presentation.v1.RegistrationStartResponse;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.RegistrationCompletionInfo;
import com.example.auth.domain.auth.service.RegistrationSessionProcessor;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.external.notification.MockVerificationCodeSender;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * LOCAL 온보딩 E2E: 시작→이메일/휴대폰 코드→Mock 본인인증→필수동의 버퍼→가입 완료 커밋→로그인→세션
 * 검증을 실 PostgreSQL·Redis로 검증한다.
 *
 * <p>보안 불변식을 함께 검증한다 — 스텝 미충족 complete 거부, 챌린지 종별 교차 제출 거부(SMS 코드로
 * 이메일 스텝 위조 불가), 온보딩 세션의 PII 원문 미보유(참조만).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class OnboardingApiE2EIT {

    private static final String NAME = "홍길동";
    private static final LocalDate BIRTH = LocalDate.of(1990, 3, 14);
    private static final String PHONE = "+821012345678";

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
    private MockVerificationCodeSender verificationCodeSender;

    @Autowired
    private RegistrationSessionProcessor registrationSessionProcessor;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void completesFullLocalOnboardingThenSignupLoginAndSessionCheckSucceed() {
        String email = "onboard-happy@example.com";
        RegistrationStartResponse started = start(email);

        verifyEmailStep(started, email);
        String phoneChallengeId = requestPhoneChallenge(started, PHONE);
        verifyPhoneStep(started, phoneChallengeId, PHONE);
        verifyIdentityStep(started);
        submitConsents(started);

        RegistrationCompletionInfo bundle =
                registrationSessionProcessor.complete(started.registrationId(), started.onboardingToken());
        assertThat(bundle.loginEmail()).isEqualTo(email);
        assertThat(bundle.verificationRef()).isNotNull();
        assertThat(bundle.ciHash()).isNotBlank();
        assertThat(bundle.consents()).hasSize(3);

        // 세션엔 PII 원문이 없다 — 필드셋이 참조·플래그만으로 정확히 구성된다.
        Map<Object, Object> fields = redisTemplate.opsForHash().entries("reg:" + started.registrationId());
        assertThat(fields.keySet())
                .containsExactlyInAnyOrder(
                        "type",
                        "tokenHash",
                        "loginEmail",
                        "emailChallengeId",
                        "phoneChallengeId",
                        "emailVerified",
                        "phoneVerified",
                        "identityVerified",
                        "requiredConsented",
                        "verificationRef",
                        "ciHash",
                        "consents");
        for (Object value : fields.values()) {
            assertThat(value.toString())
                    .doesNotContain(NAME)
                    .doesNotContain(PHONE)
                    .doesNotContain(BIRTH.toString());
        }

        // 영속 PENDING 미생성 — 완료 전까지 진행 상태는 Redis TTL 세션에만 있다.
        long usersBefore = count("select count(*) from usr.users");
        assertThat(count("select count(*) from auth.auth_account where login_email = ?", email))
                .isZero();

        ResponseEntity<RegistrationCompleteResponse> completed = rest.postForEntity(
                "/auth/registration/complete",
                new RegistrationCompleteRequest(started.registrationId(), started.onboardingToken(), "secret123"),
                RegistrationCompleteResponse.class);
        assertThat(completed.getStatusCode().value()).isEqualTo(201);
        UUID userId = requireNonNull(completed.getBody()).userId();
        assertThat(count("select count(*) from usr.users")).isEqualTo(usersBefore + 1);

        // 가입 완료 → 로그인 → 세션 검증.
        ResponseEntity<TokenResponse> loggedIn = rest.postForEntity(
                "/auth/login",
                new LoginRequest(email, "secret123", DeviceBindingRequestFixture.webDevice()),
                TokenResponse.class);
        assertThat(loggedIn.getStatusCode().value()).isEqualTo(200);
        HttpHeaders bearer = new HttpHeaders();
        bearer.setBearerAuth(requireNonNull(loggedIn.getBody()).accessToken());
        ResponseEntity<MeResponse> me =
                rest.exchange("/auth/me", HttpMethod.GET, new HttpEntity<>(bearer), MeResponse.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(requireNonNull(me.getBody()).userId()).isEqualTo(userId);

        // 성공한 완료는 세션(멱등키)을 소비한다 — 같은 registrationId 재요청은 404.
        ResponseEntity<String> replayed = rest.postForEntity(
                "/auth/registration/complete",
                new RegistrationCompleteRequest(started.registrationId(), started.onboardingToken(), "secret123"),
                String.class);
        assertThat(replayed.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rejectsCompleteWhenStepsIncomplete() {
        RegistrationStartResponse started = start("onboard-incomplete@example.com");
        verifyEmailStep(started, "onboard-incomplete@example.com");

        assertThatThrownBy(() ->
                        registrationSessionProcessor.complete(started.registrationId(), started.onboardingToken()))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.REGISTRATION_STEP_INCOMPLETE));

        ResponseEntity<String> response = rest.postForEntity(
                "/auth/registration/complete",
                new RegistrationCompleteRequest(started.registrationId(), started.onboardingToken(), "secret123"),
                String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void rejectsCrossChallengeSubmission() {
        String email = "onboard-cross@example.com";
        RegistrationStartResponse started = start(email);
        String phoneChallengeId = requestPhoneChallenge(started, PHONE);
        String smsCode = requireNonNull(verificationCodeSender.lastContent(PHONE));

        // SMS 챌린지(코드까지 정답)를 이메일 스텝에 제출 — 종별 바인딩 불일치로 거부돼야 한다.
        ResponseEntity<String> crossed = rest.postForEntity(
                "/auth/registration/email/verify",
                new RegistrationCodeVerifyRequest(
                        started.registrationId(), started.onboardingToken(), phoneChallengeId, smsCode),
                String.class);
        assertThat(crossed.getStatusCode().value()).isEqualTo(400);

        // 바인딩 대조는 소비 전이라 SMS 챌린지는 살아 있고, 제 스텝에선 여전히 유효하다.
        verifyPhoneStep(started, phoneChallengeId, PHONE);
    }

    @Test
    void rejectsWrongOnboardingTokenAsNotFound() {
        RegistrationStartResponse started = start("onboard-token@example.com");

        ResponseEntity<String> response = rest.postForEntity(
                "/auth/registration/email/challenge",
                new EmailChallengeRequest(started.registrationId(), "wrong-token"),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rejectsWrongCode() {
        RegistrationStartResponse started = start("onboard-badcode@example.com");

        ResponseEntity<String> response = rest.postForEntity(
                "/auth/registration/email/verify",
                new RegistrationCodeVerifyRequest(
                        started.registrationId(), started.onboardingToken(), started.emailChallengeId(), "badcode"),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void rejectsUnknownTermsTypeOnDeserialization() {
        RegistrationStartResponse started = start("onboard-terms@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"registrationId":"%s","onboardingToken":"%s","consents":[{"termsType":"NOT_A_TYPE","version":1}]}
                """.formatted(started.registrationId(), started.onboardingToken());

        ResponseEntity<String> response =
                rest.postForEntity("/auth/registration/consents", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void rejectsConsentsMissingRequiredTerms() {
        RegistrationStartResponse started = start("onboard-missing-consent@example.com");

        ResponseEntity<String> response = rest.postForEntity(
                "/auth/registration/consents",
                new ConsentsRequest(
                        started.registrationId(),
                        started.onboardingToken(),
                        List.of(new ConsentsRequest.ConsentItem(TermsType.SERVICE, 1))),
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private RegistrationStartResponse start(String email) {
        ResponseEntity<RegistrationStartResponse> response = rest.postForEntity(
                "/auth/registration", new RegistrationStartRequest(email), RegistrationStartResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private void verifyEmailStep(RegistrationStartResponse started, String email) {
        String code = requireNonNull(verificationCodeSender.lastContent(email));
        ResponseEntity<Void> response = rest.postForEntity(
                "/auth/registration/email/verify",
                new RegistrationCodeVerifyRequest(
                        started.registrationId(), started.onboardingToken(), started.emailChallengeId(), code),
                Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private String requestPhoneChallenge(RegistrationStartResponse started, String phone) {
        ResponseEntity<ChallengeResponse> response = rest.postForEntity(
                "/auth/registration/phone/challenge",
                new PhoneChallengeRequest(started.registrationId(), started.onboardingToken(), phone),
                ChallengeResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody()).challengeId();
    }

    private void verifyPhoneStep(RegistrationStartResponse started, String challengeId, String phone) {
        String code = requireNonNull(verificationCodeSender.lastContent(phone));
        ResponseEntity<Void> response = rest.postForEntity(
                "/auth/registration/phone/verify",
                new RegistrationCodeVerifyRequest(
                        started.registrationId(), started.onboardingToken(), challengeId, code),
                Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private void verifyIdentityStep(RegistrationStartResponse started) {
        ResponseEntity<Void> response = rest.postForEntity(
                "/auth/registration/identity/verify",
                new IdentityVerifyRequest(
                        started.registrationId(),
                        started.onboardingToken(),
                        NAME,
                        BIRTH,
                        Gender.MALE,
                        Carrier.SKT,
                        PHONE),
                Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    private void submitConsents(RegistrationStartResponse started) {
        ResponseEntity<Void> response = rest.postForEntity(
                "/auth/registration/consents",
                new ConsentsRequest(
                        started.registrationId(),
                        started.onboardingToken(),
                        List.of(
                                new ConsentsRequest.ConsentItem(TermsType.SERVICE, 1),
                                new ConsentsRequest.ConsentItem(TermsType.PRIVACY_REQUIRED, 1),
                                new ConsentsRequest.ConsentItem(TermsType.AGE14, 1))),
                Void.class);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }
}
