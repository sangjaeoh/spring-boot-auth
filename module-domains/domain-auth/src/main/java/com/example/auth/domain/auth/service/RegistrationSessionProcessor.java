package com.example.auth.domain.auth.service;

import static java.util.Objects.requireNonNull;

import com.example.auth.common.core.crypto.TokenHasher;
import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.entity.RegistrationType;
import com.example.auth.domain.auth.entity.SocialProvider;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.RegistrationCompletionInfo;
import com.example.auth.domain.auth.info.RegistrationStartedInfo;
import com.example.auth.domain.auth.info.SocialRegistrationStartedInfo;
import com.example.auth.domain.auth.port.NotificationChannel;
import com.example.auth.domain.auth.port.RegistrationSessionStore;
import com.example.auth.domain.auth.port.RegistrationSnapshot;
import com.example.auth.domain.auth.port.SocialRegistrationContext;
import com.example.auth.domain.auth.port.VerificationResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 가입 온보딩(LOCAL·SOCIAL)의 진행 상태를 조율한다(Redis 저장은 포트에 위임 — RDB 트랜잭션 없음).
 *
 * <p>영속 PENDING 계정을 만들지 않는다 — 진행 상태는 TTL 세션에만 있고, 온보딩 전용 토큰(불투명
 * 256bit 랜덤, 해시만 저장)이 이 표면의 유일한 인증이다. 세션 부재·토큰 불일치는 단일 404로 응답하고
 * 부재 경로도 동일 비용의 해시 비교를 태워 registrationId 존재 여부를 타이밍으로 흘리지 않는다.
 *
 * <p>이메일/휴대폰 코드는 {@link VerificationChallengeProcessor}를 재사용하되, 발급한 challengeId를
 * 세션에 종별로 바인딩해 verify 시 대조한다 — 챌린지는 subjectId(=registrationId)만으로는 종별 구분이
 * 안 돼, 바인딩 없이는 SMS 코드로 이메일 스텝을 위조할 수 있다. 재발송은 바인딩을 교체해 이전 발급분을
 * 무효화한다.
 */
@Service
public class RegistrationSessionProcessor {

    private final RegistrationSessionStore store;
    private final VerificationChallengeProcessor challengeProcessor;
    private final TokenHasher tokenHasher;
    private final SecureRandom random = new SecureRandom();
    private final Duration sessionTtl;
    private final String absentTokenHash;

    public RegistrationSessionProcessor(
            RegistrationSessionStore store,
            VerificationChallengeProcessor challengeProcessor,
            TokenHasher tokenHasher,
            @Value("${auth.registration.ttl-minutes:30}") long ttlMinutes) {
        this.store = store;
        this.challengeProcessor = challengeProcessor;
        this.tokenHasher = tokenHasher;
        this.sessionTtl = Duration.ofMinutes(ttlMinutes);
        this.absentTokenHash = tokenHasher.hash("registration-absent-dummy");
    }

    /**
     * LOCAL 온보딩을 시작한다 — 세션 생성 + 온보딩 토큰 발급 + 이메일 챌린지 발송.
     *
     * <p>반환된 {@code onboardingToken} 평문은 이 응답에서만 노출된다(로깅 금지).
     */
    public RegistrationStartedInfo startLocal(String loginEmail) {
        String email = Email.of(loginEmail).value();
        UUID registrationId = UuidV7Generator.generate();
        String onboardingToken = generateToken();
        // 세션 저장 후 발송(1a 챌린지와 동일 순서) — 저장 실패 시 세션 없는 코드 메일이 나가지 않는다.
        store.create(registrationId, RegistrationType.LOCAL, tokenHasher.hash(onboardingToken), email, sessionTtl);
        String emailChallengeId = challengeProcessor.issue(registrationId, NotificationChannel.EMAIL, email);
        requireAlive(store.attachEmailChallenge(registrationId, emailChallengeId));
        return new RegistrationStartedInfo(registrationId, onboardingToken, emailChallengeId, sessionTtl.toSeconds());
    }

    /**
     * SOCIAL 온보딩을 시작한다 — 검증된 소셜 신원 컨텍스트를 보관한 세션 생성 + 온보딩 토큰 발급.
     * 이메일 챌린지는 없다(이메일 소유는 IdP가 검증). PII 원문은 세션에 들이지 않는다 — subject와
     * 릴레이 플래그만 보관한다.
     *
     * <p>반환된 {@code onboardingToken} 평문은 이 응답에서만 노출된다(로깅 금지).
     */
    public SocialRegistrationStartedInfo startSocial(
            SocialProvider provider, String providerUserId, String email, boolean privateRelayEmail) {
        String loginEmail = Email.of(email).value();
        UUID registrationId = UuidV7Generator.generate();
        String onboardingToken = generateToken();
        store.createSocial(
                registrationId,
                tokenHasher.hash(onboardingToken),
                loginEmail,
                new SocialRegistrationContext(provider, providerUserId, privateRelayEmail),
                sessionTtl);
        return new SocialRegistrationStartedInfo(registrationId, onboardingToken, sessionTtl.toSeconds());
    }

    /**
     * 이메일 챌린지를 세션의 loginEmail로 재발급하고 바인딩을 교체한다.
     *
     * @throws AuthException 세션 부재·만료·토큰 불일치 시(404)
     */
    public String reissueEmailChallenge(UUID registrationId, String onboardingToken) {
        RegistrationSnapshot session = requireSession(registrationId, onboardingToken);
        String challengeId = challengeProcessor.issue(registrationId, NotificationChannel.EMAIL, session.loginEmail());
        requireAlive(store.attachEmailChallenge(registrationId, challengeId));
        return challengeId;
    }

    /**
     * 휴대폰 인증 챌린지를 발급(SMS)하고 바인딩한다.
     *
     * @throws AuthException 세션 부재·만료·토큰 불일치 시(404)
     */
    public String requestPhoneChallenge(UUID registrationId, String onboardingToken, String phone) {
        requireSession(registrationId, onboardingToken);
        String challengeId = challengeProcessor.issue(registrationId, NotificationChannel.SMS, phone);
        requireAlive(store.attachPhoneChallenge(registrationId, challengeId));
        return challengeId;
    }

    /**
     * 이메일 코드를 검증하고 이메일 스텝을 완료로 마킹한다.
     *
     * @throws AuthException 코드 불일치·만료·챌린지 불일치 시(400), 세션 부재 시(404)
     */
    public void verifyEmail(UUID registrationId, String onboardingToken, String challengeId, String code) {
        RegistrationSnapshot session = requireSession(registrationId, onboardingToken);
        verifyBoundChallenge(registrationId, session.emailChallengeId(), challengeId, code);
        requireAlive(store.markEmailVerified(registrationId));
    }

    /**
     * 휴대폰 코드를 검증하고 휴대폰 스텝을 완료로 마킹한다.
     *
     * @throws AuthException 코드 불일치·만료·챌린지 불일치 시(400), 세션 부재 시(404)
     */
    public void verifyPhone(UUID registrationId, String onboardingToken, String challengeId, String code) {
        RegistrationSnapshot session = requireSession(registrationId, onboardingToken);
        verifyBoundChallenge(registrationId, session.phoneChallengeId(), challengeId, code);
        requireAlive(store.markPhoneVerified(registrationId));
    }

    /**
     * 본인인증 스텝을 완료로 마킹하고 결과 참조({@code verificationRef}·{@code ciHash})를 보관한다.
     * PII 원문은 세션에 들이지 않는다.
     *
     * @throws AuthException 세션 부재·만료·토큰 불일치 시(404)
     */
    public void markIdentityVerified(UUID registrationId, String onboardingToken, UUID verificationRef, String ciHash) {
        requireSession(registrationId, onboardingToken);
        requireAlive(store.markIdentityVerified(registrationId, verificationRef, ciHash));
    }

    /**
     * 동의 선택값을 버퍼링하고 필수동의 스텝을 완료로 마킹한다. 선택값의 유효성(필수 커버리지·발효 버전)은
     * 호출자(파사드→유저 도메인)가 선행 검증하며, 최종 권위는 {@code CreateUser} 트랜잭션의 재검증이다.
     *
     * @throws AuthException 세션 부재·만료·토큰 불일치 시(404)
     */
    public void bufferRequiredConsents(UUID registrationId, String onboardingToken, List<ConsentSelection> consents) {
        if (consents.isEmpty()) {
            throw new IllegalArgumentException("동의 선택값이 비어 있습니다.");
        }
        requireSession(registrationId, onboardingToken);
        requireAlive(store.markRequiredConsented(registrationId, consents));
    }

    /**
     * 세션이 활성이고 토큰이 유효함을 확인한다(외부 PII 제출을 동반하는 스텝의 선차단용).
     *
     * @throws AuthException 세션 부재·만료·토큰 불일치 시(404)
     */
    public void requireActive(UUID registrationId, String onboardingToken) {
        requireSession(registrationId, onboardingToken);
    }

    /**
     * 가입유형별 필수 스텝셋 충족을 검증하고 {@code CreateUser} 재료 번들을 반환한다. 세션은 소비하지
     * 않는다 — 멱등키(registrationId)로서 다음 단계(CreateUser) 성공 후에 소비한다.
     *
     * @throws AuthException 스텝 미충족 시(409), 세션 부재·만료·토큰 불일치 시(404)
     */
    public RegistrationCompletionInfo complete(UUID registrationId, String onboardingToken) {
        RegistrationSnapshot session = requireSession(registrationId, onboardingToken);
        if (!session.type().isSatisfiedBy(session.completedSteps())) {
            throw new AuthException(AuthErrorCode.REGISTRATION_STEP_INCOMPLETE);
        }
        return new RegistrationCompletionInfo(
                registrationId,
                session.type(),
                session.loginEmail(),
                requireNonNull(session.verificationRef()),
                requireNonNull(session.ciHash()),
                session.consents(),
                session.social());
    }

    /**
     * 가입 커밋이 성공한 세션(멱등키)을 파기한다. 커밋 후 파기 실패(저장소 불가 503) 시 세션이 남지만,
     * 재요청은 {@code CreateUser}의 멱등 재실행으로 수렴한다.
     */
    public void consume(UUID registrationId) {
        store.delete(registrationId);
    }

    private void verifyBoundChallenge(
            UUID registrationId, @Nullable String boundChallengeId, String presentedChallengeId, String code) {
        // 바인딩 대조는 챌린지 소비 전이다 — 불일치 제출이 유효 챌린지를 소진시키지 않는다.
        if (!presentedChallengeId.equals(boundChallengeId)) {
            throw new AuthException(AuthErrorCode.VERIFICATION_CODE_INVALID);
        }
        VerificationResult result = challengeProcessor.verify(presentedChallengeId, code);
        if (!result.isVerified() || !registrationId.equals(result.subjectId())) {
            throw new AuthException(AuthErrorCode.VERIFICATION_CODE_INVALID);
        }
    }

    private RegistrationSnapshot requireSession(UUID registrationId, String onboardingToken) {
        String presentedHash = tokenHasher.hash(onboardingToken);
        RegistrationSnapshot session = store.find(registrationId).orElse(null);
        // 부재 경로도 동일 비용 비교를 태워 registrationId 존재 여부의 타이밍 누출을 막는다.
        String expectedHash = session == null ? absentTokenHash : session.tokenHash();
        boolean tokenMatches = MessageDigest.isEqual(
                presentedHash.getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
        if (session == null || !tokenMatches) {
            throw new AuthException(AuthErrorCode.REGISTRATION_NOT_FOUND);
        }
        return session;
    }

    private void requireAlive(boolean stillPresent) {
        if (!stillPresent) {
            throw new AuthException(AuthErrorCode.REGISTRATION_NOT_FOUND);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
