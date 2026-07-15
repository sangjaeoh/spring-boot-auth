package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.MyInfoFacade;
import com.example.auth.common.web.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 정보 API(마스킹 조회·연락용/로그인 이메일 변경).
 */
@RestController
@RequestMapping("/users/me")
public class MemberController {

    private final MyInfoFacade myInfoFacade;

    public MemberController(MyInfoFacade myInfoFacade) {
        this.myInfoFacade = myInfoFacade;
    }

    /**
     * 마스킹 적용된 내 정보를 반환한다.
     */
    @GetMapping
    public MyInfoResponse me(@AuthenticationPrincipal AuthUser user) {
        return myInfoFacade.me(user.userId());
    }

    /**
     * 연락용 이메일을 변경한다(로그인 식별자와 독립).
     */
    @PutMapping("/contact-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeContactEmail(
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChangeContactEmailRequest request) {
        myInfoFacade.changeContactEmail(user.userId(), request.email());
    }

    /**
     * 로그인 이메일을 변경한다(유니크 — 중복이면 409).
     */
    @PutMapping("/login-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeLoginEmail(
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody ChangeLoginEmailRequest request) {
        myInfoFacade.changeLoginEmail(user.userId(), request.email());
    }
}
