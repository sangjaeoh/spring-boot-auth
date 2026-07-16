package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.LoggedIn;
import com.example.auth.domain.generic.service.AuditLogAppender;
import org.springframework.stereotype.Component;

/**
 * 로그인 성공 통합 이벤트를 감사 로그에 append한다(디둡 원장과 원자 커밋 — 이벤트당 1행).
 */
@Component
public class LoggedInAuditListener implements IntegrationEventConsumer<LoggedIn> {

    private final AuditLogAppender auditLogAppender;

    public LoggedInAuditListener(AuditLogAppender auditLogAppender) {
        this.auditLogAppender = auditLogAppender;
    }

    @Override
    public String consumerId() {
        return "audit.logged-in";
    }

    @Override
    public Class<LoggedIn> eventType() {
        return LoggedIn.class;
    }

    @Override
    public void consume(LoggedIn event) {
        auditLogAppender.record(
                event.userId().toString(),
                "auth.login",
                event.userId().toString(),
                null,
                null,
                "{\"sessionId\":\"" + event.sessionId() + "\"}",
                event.occurredAt());
    }
}
