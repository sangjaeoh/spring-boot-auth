package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.PasswordFacade;
import com.example.auth.common.web.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 API(재설정 시작·완료·로그인 상태 변경).
 */
@RestController
@RequestMapping("/auth/password")
public class PasswordController {

    private final PasswordFacade passwordFacade;

    public PasswordController(PasswordFacade passwordFacade) {
        this.passwordFacade = passwordFacade;
    }

    /**
     * 비밀번호 재설정을 시작하고 인증코드를 발송한다.
     */
    @PostMapping("/reset/initiate")
    public PasswordResetInitiateResponse initiateReset(@Valid @RequestBody PasswordResetInitiateRequest request) {
        return new PasswordResetInitiateResponse(passwordFacade.initiateReset(request.email()));
    }

    /**
     * 인증코드를 검증하고 새 비밀번호를 적용한다.
     */
    @PostMapping("/reset/complete")
    public ResponseEntity<Void> completeReset(@Valid @RequestBody PasswordResetCompleteRequest request) {
        passwordFacade.completeReset(request.challengeId(), request.code(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * 현재 비밀번호를 검증하고 새 비밀번호로 변경한다.
     */
    @PostMapping("/change")
    public ResponseEntity<Void> change(
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody PasswordChangeRequest request) {
        passwordFacade.change(user.userId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
