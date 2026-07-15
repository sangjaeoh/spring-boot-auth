package com.example.auth.app.api.event.listener;

import com.example.auth.app.api.facade.NotificationFacade;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.RefreshReuseDetected;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 리프레시 재사용(탈취 의심) 감지를 소비해 보안 알림을 발송한다. 발송 실패는 예외로 전파돼 DLQ
 * 재시도로 수렴한다.
 */
@Component
public class RefreshReuseDetectedNotificationListener implements IntegrationEventConsumer<RefreshReuseDetected> {

    private final NotificationFacade notificationFacade;

    public RefreshReuseDetectedNotificationListener(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @Override
    public String consumerId() {
        return "generic-notification.refresh-reuse-detected";
    }

    @Override
    public Class<RefreshReuseDetected> eventType() {
        return RefreshReuseDetected.class;
    }

    @Override
    public void consume(RefreshReuseDetected event) {
        notificationFacade.dispatchSecurity(
                event.userId(),
                "security.refresh-reuse",
                Map.of("occurredAt", event.occurredAt().toString()),
                event.eventId());
    }
}
