package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 시 신규 기기 감지 통합 이벤트다(보안 알림 트리거 — 발송은 후속 단계).
 */
public record NewDeviceDetected(UUID eventId, UUID userId, UUID deviceId, Instant occurredAt)
        implements IntegrationEvent {

    public NewDeviceDetected(UUID userId, UUID deviceId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, deviceId, occurredAt);
    }
}
