package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 세션 무효화 통합 이벤트다(로그아웃·강제 종료).
 */
public record SessionRevoked(UUID eventId, UUID userId, UUID sessionId, Instant occurredAt)
        implements IntegrationEvent {

    public SessionRevoked(UUID userId, UUID sessionId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, sessionId, occurredAt);
    }
}
