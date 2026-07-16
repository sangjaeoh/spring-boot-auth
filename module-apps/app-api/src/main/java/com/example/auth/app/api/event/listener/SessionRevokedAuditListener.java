package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.SessionRevoked;
import com.example.auth.domain.generic.service.AuditLogAppender;
import org.springframework.stereotype.Component;

/**
 * 세션 무효화(로그아웃·원격 종료) 통합 이벤트를 감사 로그에 append한다.
 */
@Component
public class SessionRevokedAuditListener implements IntegrationEventConsumer<SessionRevoked> {

    private final AuditLogAppender auditLogAppender;

    public SessionRevokedAuditListener(AuditLogAppender auditLogAppender) {
        this.auditLogAppender = auditLogAppender;
    }

    @Override
    public String consumerId() {
        return "audit.session-revoked";
    }

    @Override
    public Class<SessionRevoked> eventType() {
        return SessionRevoked.class;
    }

    @Override
    public void consume(SessionRevoked event) {
        auditLogAppender.record(
                event.userId().toString(),
                "auth.session-revoked",
                event.userId().toString(),
                null,
                null,
                "{\"sessionId\":\"" + event.sessionId() + "\"}",
                event.occurredAt());
    }
}
