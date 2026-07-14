package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.AuthFacade;
import com.example.auth.common.web.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API(로그인·로그아웃·토큰 재발급·내 정보).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthFacade authFacade;

    public AuthController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    /**
     * 이메일·비밀번호로 로그인하고 Access·Refresh 토큰을 발급한다.
     */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authFacade.login(
                request.email(),
                request.password(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
    }

    /**
     * 현재 세션을 무효화한다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser user) {
        authFacade.logout(user.userId(), user.sessionId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 리프레시 토큰을 회전해 새 토큰을 발급한다.
     */
    @PostMapping("/token/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authFacade.refresh(request.refreshToken());
    }

    /**
     * 현재 인증 주체를 반환한다.
     */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return new MeResponse(user.userId(), user.roles());
    }
}
