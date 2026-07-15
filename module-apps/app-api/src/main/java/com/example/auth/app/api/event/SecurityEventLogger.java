package com.example.auth.app.api.event;

import com.example.auth.domain.auth.event.LoggedIn;
import com.example.auth.domain.auth.event.LoginEmailChanged;
import com.example.auth.domain.auth.event.LoginFailed;
import com.example.auth.domain.auth.event.PasswordChanged;
import com.example.auth.domain.auth.event.PasswordResetCompleted;
import com.example.auth.domain.auth.event.PasswordResetRequested;
import com.example.auth.domain.auth.event.RefreshReuseDetected;
import com.example.auth.domain.auth.event.SessionRevoked;
import com.example.auth.domain.auth.event.SocialConnected;
import com.example.auth.domain.auth.event.SocialDisconnected;
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

    @EventListener
    public void onPasswordResetRequested(PasswordResetRequested event) {
        log.info("비밀번호 재설정 요청 userId={}", event.userId());
    }

    @EventListener
    public void onPasswordResetCompleted(PasswordResetCompleted event) {
        log.info("비밀번호 재설정 완료 userId={}", event.userId());
    }

    @EventListener
    public void onPasswordChanged(PasswordChanged event) {
        log.info("비밀번호 변경 userId={}", event.userId());
    }

    @EventListener
    public void onSocialConnected(SocialConnected event) {
        log.info("소셜 연동 userId={} provider={}", event.userId(), event.provider());
    }

    @EventListener
    public void onSocialDisconnected(SocialDisconnected event) {
        log.info("소셜 연동 해제 userId={} provider={}", event.userId(), event.provider());
    }

    @EventListener
    public void onLoginEmailChanged(LoginEmailChanged event) {
        log.info("로그인 이메일 변경 userId={}", event.userId());
    }
}
