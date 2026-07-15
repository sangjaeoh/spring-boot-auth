package com.example.auth.app.api.event.listener;

import com.example.auth.app.api.facade.NotificationFacade;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.NewDeviceDetected;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 신규 기기 로그인 감지를 소비해 보안 알림을 발송한다. 발송 실패는 예외로 전파돼 DLQ 재시도로
 * 수렴한다.
 */
@Component
public class NewDeviceDetectedNotificationListener implements IntegrationEventConsumer<NewDeviceDetected> {

    private final NotificationFacade notificationFacade;

    public NewDeviceDetectedNotificationListener(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @Override
    public String consumerId() {
        return "generic-notification.new-device-detected";
    }

    @Override
    public Class<NewDeviceDetected> eventType() {
        return NewDeviceDetected.class;
    }

    @Override
    public void consume(NewDeviceDetected event) {
        notificationFacade.dispatchSecurity(
                event.userId(),
                "security.new-device",
                Map.of(
                        "deviceId", event.deviceId().toString(),
                        "occurredAt", event.occurredAt().toString()),
                event.eventId());
    }
}
