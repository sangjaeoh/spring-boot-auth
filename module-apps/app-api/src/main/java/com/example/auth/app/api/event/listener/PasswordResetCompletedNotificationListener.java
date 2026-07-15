package com.example.auth.app.api.event.listener;

import com.example.auth.app.api.facade.NotificationFacade;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.PasswordResetCompleted;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 완료를 소비해 보안 알림을 발송한다(재설정 요청 단계는 OTP 메일 자체가 통지라 완료만
 * 구독한다). 발송 실패는 예외로 전파돼 DLQ 재시도로 수렴한다.
 */
@Component
public class PasswordResetCompletedNotificationListener implements IntegrationEventConsumer<PasswordResetCompleted> {

    private final NotificationFacade notificationFacade;

    public PasswordResetCompletedNotificationListener(NotificationFacade notificationFacade) {
        this.notificationFacade = notificationFacade;
    }

    @Override
    public String consumerId() {
        return "generic-notification.password-reset-completed";
    }

    @Override
    public Class<PasswordResetCompleted> eventType() {
        return PasswordResetCompleted.class;
    }

    @Override
    public void consume(PasswordResetCompleted event) {
        notificationFacade.dispatchSecurity(
                event.userId(),
                "security.password-reset-completed",
                Map.of("occurredAt", event.occurredAt().toString()),
                event.eventId());
    }
}
