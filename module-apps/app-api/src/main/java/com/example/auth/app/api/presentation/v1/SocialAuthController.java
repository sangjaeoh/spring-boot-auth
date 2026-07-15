package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.SocialAuthFacade;
import com.example.auth.common.web.security.AuthUser;
import com.example.auth.domain.auth.entity.SocialProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인·SOCIAL 온보딩 완료·연동/해제 API. 로그인·가입 완료는 permitAll(온보딩 토큰이 표면 인증),
 * 연동/해제는 정식 Access 인증이 필요하다. SOCIAL 온보딩의 중간 스텝(본인인증·동의)은 기존
 * {@code /auth/registration} 엔드포인트를 그대로 쓴다.
 */
@RestController
@RequestMapping("/auth/social")
public class SocialAuthController {

    private final SocialAuthFacade socialAuthFacade;

    public SocialAuthController(SocialAuthFacade socialAuthFacade) {
        this.socialAuthFacade = socialAuthFacade;
    }

    /**
     * {@code id_token}으로 로그인한다 — 기존 연동이면 즉시 토큰 발급, 미연동 신규면 SOCIAL 온보딩 유도.
     */
    @PostMapping("/login")
    public SocialLoginResponse login(@Valid @RequestBody SocialLoginRequest request, HttpServletRequest httpRequest) {
        return socialAuthFacade.login(
                request.provider(),
                request.idToken(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
    }

    /**
     * SOCIAL 가입을 완료한다 — 유저·인증 계정·소셜 연동 원자 생성(비밀번호 없는 계정) 후 로그인 세션을
     * 발급한다.
     */
    @PostMapping("/registration/complete")
    @ResponseStatus(HttpStatus.CREATED)
    public SocialRegistrationCompleteResponse completeRegistration(
            @Valid @RequestBody SocialRegistrationCompleteRequest request, HttpServletRequest httpRequest) {
        return socialAuthFacade.completeRegistration(
                request.registrationId(),
                request.onboardingToken(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
    }

    /**
     * 현재 계정에 소셜 수단을 연동한다.
     */
    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void connect(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody SocialConnectRequest request) {
        socialAuthFacade.connect(user.userId(), request.provider(), request.idToken());
    }

    /**
     * 소셜 연동을 해제한다. 마지막 로그인 수단이면 409로 거부한다.
     */
    @DeleteMapping("/connections/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal AuthUser user, @PathVariable SocialProvider provider) {
        socialAuthFacade.disconnect(user.userId(), provider);
    }
}
