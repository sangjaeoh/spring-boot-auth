package com.example.auth.app.api.facade;

import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.info.RegistrationCompletionInfo;
import com.example.auth.domain.auth.info.RegistrationStartedInfo;
import com.example.auth.domain.auth.service.AccountRegistrationProcessor;
import com.example.auth.domain.auth.service.PasswordPolicyValidator;
import com.example.auth.domain.auth.service.RegistrationSessionProcessor;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.info.IdentityVerifiedInfo;
import com.example.auth.domain.user.port.IdentityProviderRequest;
import com.example.auth.domain.user.service.ConsentValidator;
import com.example.auth.domain.user.service.IdentityVerificationProcessor;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * LOCAL 가입 온보딩을 조율한다 — 인증(세션·코드)과 유저(본인인증·약관) 도메인의 크로스 도메인 seam이다
 * (트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지 않는다).
 *
 * <p>PII를 동반하는 스텝(본인인증)은 유저 도메인 진입 전에 세션·토큰을 선검증해, 미인증 요청이 기관
 * 호출·PII 행 생성을 유발하지 못하게 한다.
 */
@Component
public class OnboardingFacade {

    private final RegistrationSessionProcessor registrationSessionProcessor;
    private final IdentityVerificationProcessor identityVerificationProcessor;
    private final ConsentValidator consentValidator;
    private final AccountRegistrationProcessor accountRegistrationProcessor;
    private final PasswordPolicyValidator passwordPolicyValidator;

    public OnboardingFacade(
            RegistrationSessionProcessor registrationSessionProcessor,
            IdentityVerificationProcessor identityVerificationProcessor,
            ConsentValidator consentValidator,
            AccountRegistrationProcessor accountRegistrationProcessor,
            PasswordPolicyValidator passwordPolicyValidator) {
        this.registrationSessionProcessor = registrationSessionProcessor;
        this.identityVerificationProcessor = identityVerificationProcessor;
        this.consentValidator = consentValidator;
        this.accountRegistrationProcessor = accountRegistrationProcessor;
        this.passwordPolicyValidator = passwordPolicyValidator;
    }

    /**
     * LOCAL 온보딩을 시작한다(세션 + 온보딩 토큰 + 이메일 챌린지 발송).
     */
    public RegistrationStartedInfo start(String loginEmail) {
        return registrationSessionProcessor.startLocal(loginEmail);
    }

    /**
     * 이메일 챌린지를 재발급한다.
     */
    public String reissueEmailChallenge(UUID registrationId, String onboardingToken) {
        return registrationSessionProcessor.reissueEmailChallenge(registrationId, onboardingToken);
    }

    /**
     * 이메일 코드를 검증하고 이메일 스텝을 완료한다.
     */
    public void verifyEmail(UUID registrationId, String onboardingToken, String challengeId, String code) {
        registrationSessionProcessor.verifyEmail(registrationId, onboardingToken, challengeId, code);
    }

    /**
     * 휴대폰 인증 챌린지를 발급한다(SMS).
     */
    public String requestPhoneChallenge(UUID registrationId, String onboardingToken, String phone) {
        return registrationSessionProcessor.requestPhoneChallenge(registrationId, onboardingToken, phone);
    }

    /**
     * 휴대폰 코드를 검증하고 휴대폰 스텝을 완료한다.
     */
    public void verifyPhone(UUID registrationId, String onboardingToken, String challengeId, String code) {
        registrationSessionProcessor.verifyPhone(registrationId, onboardingToken, challengeId, code);
    }

    /**
     * 본인인증을 수행하고 본인인증 스텝을 완료한다. 세션에는 결과 참조(verificationRef·ciHash)만 남는다.
     */
    public void verifyIdentity(
            UUID registrationId,
            String onboardingToken,
            String name,
            LocalDate birthDate,
            Gender gender,
            Carrier carrier,
            String phone) {
        registrationSessionProcessor.requireActive(registrationId, onboardingToken);
        IdentityVerifiedInfo verified = identityVerificationProcessor.verify(
                new IdentityProviderRequest(name, birthDate, gender, carrier, phone));
        registrationSessionProcessor.markIdentityVerified(
                registrationId, onboardingToken, verified.verificationId(), verified.ciHash());
    }

    /**
     * 동의 선택값을 현행 약관에 대조(조기 검증)한 뒤 세션에 버퍼링하고 필수동의 스텝을 완료한다.
     * 정회원 생성 전이라 {@code ConsentRecord}는 기록하지 않으며, 최종 권위는 {@code CreateUser}
     * 트랜잭션의 재검증이다.
     */
    public void bufferConsents(UUID registrationId, String onboardingToken, Map<TermsType, Integer> agreed) {
        registrationSessionProcessor.requireActive(registrationId, onboardingToken);
        consentValidator.validateForSignup(agreed);
        List<ConsentSelection> selections = new ArrayList<>();
        agreed.forEach((type, version) -> selections.add(new ConsentSelection(type.name(), version)));
        registrationSessionProcessor.bufferRequiredConsents(registrationId, onboardingToken, selections);
    }

    /**
     * 온보딩을 정회원으로 커밋한다 — 스텝셋 충족 번들에 비밀번호를 더해 유저·인증을 단일 트랜잭션으로
     * 원자 생성하고, 성공 후 세션(멱등키)을 소비한다. 소비된 세션의 재요청은 404이며, 커밋과 소비 사이
     * 장애로 세션이 남은 재요청은 멱등 재실행으로 같은 {@code UserId}를 돌려받는다.
     *
     * <p>비밀번호 정책은 트랜잭션 진입 전 선검증한다(회복 가능한 오류로 크로스스키마 쓰기·롤백을
     * 만들지 않는다 — 최종 권위는 자격증명 등록의 재검증).
     */
    public UUID complete(UUID registrationId, String onboardingToken, String rawPassword) {
        passwordPolicyValidator.validate(rawPassword);
        RegistrationCompletionInfo completion = registrationSessionProcessor.complete(registrationId, onboardingToken);
        UUID userId = accountRegistrationProcessor.register(completion, rawPassword);
        registrationSessionProcessor.consume(registrationId);
        return userId;
    }
}
