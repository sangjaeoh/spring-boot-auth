package com.example.auth.app.api.facade;

import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.info.RegistrationStartedInfo;
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

    public OnboardingFacade(
            RegistrationSessionProcessor registrationSessionProcessor,
            IdentityVerificationProcessor identityVerificationProcessor,
            ConsentValidator consentValidator) {
        this.registrationSessionProcessor = registrationSessionProcessor;
        this.identityVerificationProcessor = identityVerificationProcessor;
        this.consentValidator = consentValidator;
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
}
