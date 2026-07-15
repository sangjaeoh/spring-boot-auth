package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.ForceLogoutRequested;
import com.example.auth.domain.auth.service.SessionProcessor;
import org.springframework.stereotype.Component;

/**
 * 관리자 강제 로그아웃 명령을 소비해 사용자의 전 세션을 즉시 무효화한다. 세션 스토어 불가 시 예외가
 * 전파되어 DLQ 재시도로 수렴한다(무효화 유실 없음).
 */
@Component
public class ForceLogoutRequestedListener implements IntegrationEventConsumer<ForceLogoutRequested> {

    private final SessionProcessor sessionProcessor;

    public ForceLogoutRequestedListener(SessionProcessor sessionProcessor) {
        this.sessionProcessor = sessionProcessor;
    }

    @Override
    public String consumerId() {
        return "auth-session.force-logout";
    }

    @Override
    public Class<ForceLogoutRequested> eventType() {
        return ForceLogoutRequested.class;
    }

    @Override
    public void consume(ForceLogoutRequested event) {
        sessionProcessor.revokeAll(event.userId());
    }
}
