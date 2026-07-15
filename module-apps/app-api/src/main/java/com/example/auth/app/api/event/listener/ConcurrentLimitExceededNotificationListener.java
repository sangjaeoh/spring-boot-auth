package com.example.auth.app.api.event.listener;

import com.example.auth.app.api.facade.NotificationFacade;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.ConcurrentLimitExceeded;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 동시 세션 상한 초과(최오래 세션 축출)를 소비해 보안 알림을 발송한다. 발송 실패는 예외로 전파돼
 * DLQ 재시도로 수렴한다.
 */
@Component
public class ConcurrentLimitExceededNotificationListener implements IntegrationEventConsumer<ConcurrentLimitExceeded> {

    private final NotificationFacade notificationFacade;

    public ConcurrentLimitExceededNotificationListener(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @Override
    public String consumerId() {
        return "generic-notification.concurrent-limit-exceeded";
    }

    @Override
    public Class<ConcurrentLimitExceeded> eventType() {
        return ConcurrentLimitExceeded.class;
    }

    @Override
    public void consume(ConcurrentLimitExceeded event) {
        notificationFacade.dispatchSecurity(
                event.userId(),
                "security.concurrent-limit",
                Map.of(
                        "evictedSessions",
                                String.valueOf(event.evictedSessionIds().size()),
                        "occurredAt", event.occurredAt().toString()),
                event.eventId());
    }
}
