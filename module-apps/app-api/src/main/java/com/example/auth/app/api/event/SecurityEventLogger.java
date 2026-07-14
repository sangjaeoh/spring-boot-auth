package com.example.auth.app.api.event;

import com.example.auth.domain.auth.event.LoggedIn;
import com.example.auth.domain.auth.event.LoginFailed;
import com.example.auth.domain.auth.event.RefreshReuseDetected;
import com.example.auth.domain.auth.event.SessionRevoked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 보안 관련 도메인 이벤트를 구조적 로그로 남긴다(관측성 증분 — 알림·감사 소비는 후속 단계).
 */
@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventLogger.class);

    @EventListener
    public void onLoggedIn(LoggedIn event) {
        log.info("로그인 성공 userId={} sessionId={}", event.userId(), event.sessionId());
    }

    @EventListener
    public void onLoginFailed(LoginFailed event) {
        log.info("로그인 실패 userId={} reason={}", event.userId(), event.reason());
    }

    @EventListener
    public void onSessionRevoked(SessionRevoked event) {
        log.info("세션 무효화 userId={} sessionId={}", event.userId(), event.sessionId());
    }

    @EventListener
    public void onRefreshReuseDetected(RefreshReuseDetected event) {
        log.warn("리프레시 재사용(탈취) 감지 — 패밀리 무효화 userId={} sessionId={}", event.userId(), event.sessionId());
    }
}
