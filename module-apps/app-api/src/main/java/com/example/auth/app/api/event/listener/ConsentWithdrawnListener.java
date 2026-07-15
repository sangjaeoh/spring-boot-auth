package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.event.ConsentWithdrawn;
import com.example.auth.domain.user.service.NotificationPreferenceModifier;
import org.springframework.stereotype.Component;

/**
 * 마케팅 동의 철회를 알림 수신 설정의 마케팅 매트릭스에 반영한다(비마케팅 철회는 무시).
 */
@Component
public class ConsentWithdrawnListener implements IntegrationEventConsumer<ConsentWithdrawn> {

    private final NotificationPreferenceModifier notificationPreferenceModifier;

    public ConsentWithdrawnListener(NotificationPreferenceModifier notificationPreferenceModifier) {
        this.notificationPreferenceModifier = notificationPreferenceModifier;
    }

    @Override
    public String consumerId() {
        return "user-notification-preference.consent-withdrawn";
    }

    @Override
    public Class<ConsentWithdrawn> eventType() {
        return ConsentWithdrawn.class;
    }

    @Override
    public void consume(ConsentWithdrawn event) {
        if (event.termsType() != TermsType.MARKETING) {
            return;
        }
        notificationPreferenceModifier.syncMarketingFromConsent(event.userId(), false, null);
    }
}
