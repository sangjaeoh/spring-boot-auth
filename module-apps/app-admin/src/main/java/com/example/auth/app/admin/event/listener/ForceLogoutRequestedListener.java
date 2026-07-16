package com.example.auth.app.admin.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.ForceLogoutRequested;
import com.example.auth.domain.auth.service.SessionProcessor;
import org.springframework.stereotype.Component;

/**
 * 관리자 강제 로그아웃 명령을 소비해 사용자의 전 세션을 즉시 무효화한다(세션 저장소는 앱 간 공유 Redis —
 * app-api 발급 세션도 함께 전멸). app-api의 동일 논리 소비자와 {@code consumerId}를 공유해 디둡 원장이
 * 하나의 소비자로 수렴한다.
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
