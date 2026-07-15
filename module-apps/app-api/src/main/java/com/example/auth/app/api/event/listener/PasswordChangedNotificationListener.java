package com.example.auth.app.api.event.listener;

import com.example.auth.app.api.facade.NotificationFacade;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.PasswordChanged;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 변경을 소비해 보안 알림을 발송한다. 발송 실패는 예외로 전파돼 DLQ 재시도로 수렴한다.
 */
@Component
public class PasswordChangedNotificationListener implements IntegrationEventConsumer<PasswordChanged> {

    private final NotificationFacade notificationFacade;

    public PasswordChangedNotificationListener(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @Override
    public String consumerId() {
        return "generic-notification.password-changed";
    }

    @Override
    public Class<PasswordChanged> eventType() {
        return PasswordChanged.class;
    }

    @Override
    public void consume(PasswordChanged event) {
        notificationFacade.dispatchSecurity(
                event.userId(),
                "security.password-changed",
                Map.of("occurredAt", event.occurredAt().toString()),
                event.eventId());
    }
}
