package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.LoginFailed;
import com.example.auth.domain.generic.service.AuditLogAppender;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 로그인 실패 통합 이벤트를 감사 로그에 append한다. 계정 미존재 실패는 주체가 없어 행위자를
 * {@code ANONYMOUS}로 기록한다.
 */
@Component
public class LoginFailedAuditListener implements IntegrationEventConsumer<LoginFailed> {

    private static final String ANONYMOUS_ACTOR = "ANONYMOUS";

    private final AuditLogAppender auditLogAppender;

    public LoginFailedAuditListener(AuditLogAppender auditLogAppender) {
        this.auditLogAppender = auditLogAppender;
    }

    @Override
    public String consumerId() {
        return "audit.login-failed";
    }

    @Override
    public Class<LoginFailed> eventType() {
        return LoginFailed.class;
    }

    @Override
    public void consume(LoginFailed event) {
        UUID userId = event.userId();
        String subject = userId == null ? null : userId.toString();
        auditLogAppender.record(
                subject == null ? ANONYMOUS_ACTOR : subject,
                "auth.login-failed",
                subject,
                null,
                null,
                "{\"reason\":\"" + event.reason() + "\"}",
                event.occurredAt());
    }
}
