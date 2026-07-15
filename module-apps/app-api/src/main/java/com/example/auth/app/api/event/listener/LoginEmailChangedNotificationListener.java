package com.example.auth.app.api.event.listener;

import com.example.auth.app.api.facade.NotificationFacade;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.LoginEmailChanged;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 로그인 이메일 변경을 소비해 보안 알림을 발송한다. 발송 실패는 예외로 전파돼 DLQ 재시도로 수렴한다.
 */
@Component
public class LoginEmailChangedNotificationListener implements IntegrationEventConsumer<LoginEmailChanged> {

    private final NotificationFacade notificationFacade;

    public LoginEmailChangedNotificationListener(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @Override
    public String consumerId() {
        return "generic-notification.login-email-changed";
    }

    @Override
    public Class<LoginEmailChanged> eventType() {
        return LoginEmailChanged.class;
    }

    @Override
    public void consume(LoginEmailChanged event) {
        notificationFacade.dispatchSecurity(
                event.userId(),
                "security.login-email-changed",
                Map.of("occurredAt", event.occurredAt().toString()),
                event.eventId());
    }
}
