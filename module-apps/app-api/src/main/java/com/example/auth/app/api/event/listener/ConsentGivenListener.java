package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.event.ConsentGiven;
import com.example.auth.domain.user.service.NotificationPreferenceModifier;
import org.springframework.stereotype.Component;

/**
 * 마케팅 동의를 알림 수신 설정의 마케팅 매트릭스에 반영한다(비마케팅 동의는 무시).
 */
@Component
public class ConsentGivenListener implements IntegrationEventConsumer<ConsentGiven> {

    private final NotificationPreferenceModifier notificationPreferenceModifier;

    public ConsentGivenListener(NotificationPreferenceModifier notificationPreferenceModifier) {
        this.notificationPreferenceModifier = notificationPreferenceModifier;
    }

    @Override
    public String consumerId() {
        return "user-notification-preference.consent-given";
    }

    @Override
    public Class<ConsentGiven> eventType() {
        return ConsentGiven.class;
    }

    @Override
    public void consume(ConsentGiven event) {
        if (event.termsType() != TermsType.MARKETING) {
            return;
        }
        notificationPreferenceModifier.syncMarketingFromConsent(event.userId(), true, event.channel());
    }
}
