package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 기기 등록 통합 이벤트다.
 */
public record DeviceRegistered(UUID eventId, UUID userId, UUID deviceId, Instant occurredAt)
        implements IntegrationEvent {

    public DeviceRegistered(UUID userId, UUID deviceId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, deviceId, occurredAt);
    }
}
