package com.example.auth.domain.user.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.TermsType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 이용 중 동의 통합 이벤트다. 마케팅(MARKETING) 동의는 알림 수신 설정의 마케팅 매트릭스 동기화가
 * 소비한다({@code channel} 지정 시 해당 채널만).
 */
public record ConsentGiven(
        UUID eventId,
        UUID userId,
        TermsType termsType,
        int termsVersion,
        @Nullable NotificationChannel channel,
        Instant occurredAt)
        implements IntegrationEvent {

    public ConsentGiven(
            UUID userId,
            TermsType termsType,
            int termsVersion,
            @Nullable NotificationChannel channel,
            Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, termsType, termsVersion, channel, occurredAt);
    }
}
