package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.SessionFacade;
import com.example.auth.common.web.security.AuthUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 세션 API(목록·원격 로그아웃).
 */
@RestController
@RequestMapping("/auth/sessions")
public class SessionController {

    private final SessionFacade sessionFacade;

    public SessionController(SessionFacade sessionFacade) {
        this.sessionFacade = sessionFacade;
    }

    /**
     * 내 활성 세션 목록을 최근 접속 순으로 반환한다(기기·IP·최근 접속, 현재 세션 표시).
     */
    @GetMapping
    public List<SessionResponse> sessions(@AuthenticationPrincipal AuthUser user) {
        return sessionFacade.sessions(user.userId(), user.sessionId());
    }

    /**
     * 특정 세션을 원격 종료한다(해당 세션의 토큰은 즉시 401).
     */
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AuthUser user, @PathVariable UUID sessionId) {
        sessionFacade.revoke(user.userId(), sessionId);
    }

    /**
     * 현재 세션을 제외한 내 세션 전체를 종료한다.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOthers(@AuthenticationPrincipal AuthUser user) {
        sessionFacade.revokeOthers(user.userId(), user.sessionId());
    }
}
