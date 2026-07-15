package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 계정 잠금 해제 통합 이벤트다(쿨다운 경과 자동 해제). 감사가 구독한다.
 */
public record AccountUnlocked(UUID eventId, UUID userId, Instant occurredAt) implements IntegrationEvent {

    public AccountUnlocked(UUID userId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, occurredAt);
    }
}
