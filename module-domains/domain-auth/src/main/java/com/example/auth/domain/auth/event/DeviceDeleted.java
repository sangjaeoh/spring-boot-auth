package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 기기 삭제 통합 이벤트다. 소비 측이 해당 기기의 세션을 종료한다({@code revokeByDevice} 교차 —
 * DOMAIN_MODEL §2.5).
 */
public record DeviceDeleted(UUID eventId, UUID userId, UUID deviceId, Instant occurredAt) implements IntegrationEvent {

    public DeviceDeleted(UUID userId, UUID deviceId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, deviceId, occurredAt);
    }
}
