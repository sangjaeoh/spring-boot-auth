package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.RefreshRotated;
import com.example.auth.domain.generic.service.AuditLogAppender;
import org.springframework.stereotype.Component;

/**
 * 리프레시 회전(토큰 재발급) 통합 이벤트를 감사 로그에 append한다.
 */
@Component
public class RefreshRotatedAuditListener implements IntegrationEventConsumer<RefreshRotated> {

    private final AuditLogAppender auditLogAppender;

    public RefreshRotatedAuditListener(AuditLogAppender auditLogAppender) {
        this.auditLogAppender = auditLogAppender;
    }

    @Override
    public String consumerId() {
        return "audit.refresh-rotated";
    }

    @Override
    public Class<RefreshRotated> eventType() {
        return RefreshRotated.class;
    }

    @Override
    public void consume(RefreshRotated event) {
        auditLogAppender.record(
                event.userId().toString(),
                "auth.token-refreshed",
                event.userId().toString(),
                null,
                null,
                "{\"sessionId\":\"" + event.sessionId() + "\"}",
                event.occurredAt());
    }
}
