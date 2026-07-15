package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.OnboardingFacade;
import com.example.auth.domain.user.entity.TermsType;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * LOCAL 가입 온보딩 API. 이 표면의 인증은 시작 시 발급되는 온보딩 전용 토큰이다(정식 Access/Refresh
 * 아님 — permitAll 라우트).
 */
@RestController
@RequestMapping("/auth/registration")
public class OnboardingController {

    private final OnboardingFacade onboardingFacade;

    public OnboardingController(OnboardingFacade onboardingFacade) {
        this.onboardingFacade = onboardingFacade;
    }

    /**
     * 가입 온보딩을 시작한다 — 세션·온보딩 토큰 발급 + 이메일 인증코드 발송.
     */
    @PostMapping
    public RegistrationStartResponse start(@Valid @RequestBody RegistrationStartRequest request) {
        return RegistrationStartResponse.from(onboardingFacade.start(request.loginEmail()));
    }

    /**
     * 이메일 인증코드를 재발송한다.
     */
    @PostMapping("/email/challenge")
    public ChallengeResponse reissueEmailChallenge(@Valid @RequestBody EmailChallengeRequest request) {
        return new ChallengeResponse(
                onboardingFacade.reissueEmailChallenge(request.registrationId(), request.onboardingToken()));
    }

    /**
     * 이메일 인증코드를 검증한다.
     */
    @PostMapping("/email/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody RegistrationCodeVerifyRequest request) {
        onboardingFacade.verifyEmail(
                request.registrationId(), request.onboardingToken(), request.challengeId(), request.code());
    }

    /**
     * 휴대폰 인증코드를 발송한다.
     */
    @PostMapping("/phone/challenge")
    public ChallengeResponse requestPhoneChallenge(@Valid @RequestBody PhoneChallengeRequest request) {
        return new ChallengeResponse(onboardingFacade.requestPhoneChallenge(
                request.registrationId(), request.onboardingToken(), request.phone()));
    }

    /**
     * 휴대폰 인증코드를 검증한다.
     */
    @PostMapping("/phone/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyPhone(@Valid @RequestBody RegistrationCodeVerifyRequest request) {
        onboardingFacade.verifyPhone(
                request.registrationId(), request.onboardingToken(), request.challengeId(), request.code());
    }

    /**
     * 본인인증을 수행한다(dev/test는 Mock 기관).
     */
    @PostMapping("/identity/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyIdentity(@Valid @RequestBody IdentityVerifyRequest request) {
        onboardingFacade.verifyIdentity(
                request.registrationId(),
                request.onboardingToken(),
                request.name(),
                request.birthDate(),
                request.gender(),
                request.carrier(),
                request.phone());
    }

    /**
     * 약관 동의를 제출한다(정회원 생성 전 버퍼링 — 기록은 가입 완료 트랜잭션에서).
     */
    @PostMapping("/consents")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submitConsents(@Valid @RequestBody ConsentsRequest request) {
        Map<TermsType, Integer> agreed = new LinkedHashMap<>();
        for (ConsentsRequest.ConsentItem item : request.consents()) {
            agreed.put(item.termsType(), item.version());
        }
        onboardingFacade.bufferConsents(request.registrationId(), request.onboardingToken(), agreed);
    }

    /**
     * 가입을 완료한다 — 유저·인증 원자 생성(단일 트랜잭션) 후 세션을 소비한다.
     *
     * <p>사용 중인 로그인 이메일이면 409를 명시 응답한다 — 이 표면은 온보딩 토큰과 이메일 소유 검증을
     * 통과한 요청자만 도달하므로, 미인증 열거 프로브를 막는 재설정 시작의 동형 응답과 달리 존재 노출이
     * 아니다.
     */
    @PostMapping("/complete")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationCompleteResponse complete(@Valid @RequestBody RegistrationCompleteRequest request) {
        return new RegistrationCompleteResponse(
                onboardingFacade.complete(request.registrationId(), request.onboardingToken(), request.password()));
    }
}
