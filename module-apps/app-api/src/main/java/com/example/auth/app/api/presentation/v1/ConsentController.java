package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.ConsentFacade;
import com.example.auth.common.web.security.AuthUser;
import com.example.auth.domain.user.entity.TermsType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 약관 동의 API(현재 스냅샷·재동의 필요 목록 조회, 이용 중 동의/철회).
 */
@RestController
@RequestMapping("/users/me/consents")
public class ConsentController {

    private final ConsentFacade consentFacade;

    public ConsentController(ConsentFacade consentFacade) {
        this.consentFacade = consentFacade;
    }

    /**
     * 현재 동의 스냅샷과 필수 약관 재동의 필요 목록을 반환한다(재동의 필요가 비면 이용 게이트 통과).
     */
    @GetMapping
    public ConsentOverviewResponse overview(@AuthenticationPrincipal AuthUser user) {
        return consentFacade.overview(user.userId());
    }

    /**
     * 현행 버전에 대한 동의(재동의 포함)를 기록한다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void agree(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AgreeConsentRequest request) {
        consentFacade.agree(user.userId(), request.termsType(), request.termsVersion(), request.channel());
    }

    /**
     * 선택 약관 동의를 철회한다(필수 약관은 400 — 탈퇴 절차 안내).
     */
    @DeleteMapping("/{termsType}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal AuthUser user, @PathVariable TermsType termsType) {
        consentFacade.withdraw(user.userId(), termsType);
    }
}
