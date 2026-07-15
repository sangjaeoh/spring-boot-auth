package com.example.auth.app.api.event.listener;

import com.example.auth.app.api.facade.NotificationFacade;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.AccountLocked;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 계정 일시 잠금을 소비해 보안 알림을 발송한다. 발송 실패는 예외로 전파돼 DLQ 재시도로 수렴한다.
 */
@Component
public class AccountLockedNotificationListener implements IntegrationEventConsumer<AccountLocked> {

    private final NotificationFacade notificationFacade;

    public AccountLockedNotificationListener(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @Override
    public String consumerId() {
        return "generic-notification.account-locked";
    }

    @Override
    public Class<AccountLocked> eventType() {
        return AccountLocked.class;
    }

    @Override
    public void consume(AccountLocked event) {
        notificationFacade.dispatchSecurity(
                event.userId(),
                "security.account-locked",
                Map.of("occurredAt", event.occurredAt().toString()),
                event.eventId());
    }
}
