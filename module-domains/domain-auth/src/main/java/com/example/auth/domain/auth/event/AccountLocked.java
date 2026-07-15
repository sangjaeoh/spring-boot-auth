package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 계정 일시 잠금 통합 이벤트다(연속 로그인 실패 임계 초과). 알림(보안)·감사가 구독한다.
 */
public record AccountLocked(UUID eventId, UUID userId, Instant occurredAt) implements IntegrationEvent {

    public AccountLocked(UUID userId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, occurredAt);
    }
}
