package com.example.auth.app.api.event.listener;

import com.example.auth.app.api.facade.NotificationFacade;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.NewLocationDetected;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 신규 지역 로그인 감지를 소비해 보안 알림을 발송한다. 발송 실패는 예외로 전파돼 DLQ 재시도로
 * 수렴한다.
 */
@Component
public class NewLocationDetectedNotificationListener implements IntegrationEventConsumer<NewLocationDetected> {

    private final NotificationFacade notificationFacade;

    public NewLocationDetectedNotificationListener(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @Override
    public String consumerId() {
        return "generic-notification.new-location-detected";
    }

    @Override
    public Class<NewLocationDetected> eventType() {
        return NewLocationDetected.class;
    }

    @Override
    public void consume(NewLocationDetected event) {
        notificationFacade.dispatchSecurity(
                event.userId(),
                "security.new-location",
                Map.of(
                        "countryCode", event.countryCode(),
                        "occurredAt", event.occurredAt().toString()),
                event.eventId());
    }
}
