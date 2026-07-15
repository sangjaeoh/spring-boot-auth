package com.example.auth.domain.user.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.domain.user.entity.NotificationCategory;
import com.example.auth.domain.user.entity.NotificationChannel;
import java.time.Instant;
import java.util.UUID;

/**
 * 알림 수신 설정 변경 통합 이벤트다(채널×카테고리 단위 허용/거부).
 */
public record NotificationPreferenceChanged(
        UUID eventId,
        UUID userId,
        NotificationChannel channel,
        NotificationCategory category,
        boolean allowed,
        Instant occurredAt)
        implements IntegrationEvent {

    public NotificationPreferenceChanged(
            UUID userId,
            NotificationChannel channel,
            NotificationCategory category,
            boolean allowed,
            Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, channel, category, allowed, occurredAt);
    }
}
