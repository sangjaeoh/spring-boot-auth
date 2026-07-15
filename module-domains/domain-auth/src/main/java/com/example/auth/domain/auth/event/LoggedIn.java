package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 성공 통합 이벤트다.
 */
public record LoggedIn(UUID eventId, UUID userId, UUID sessionId, Instant occurredAt) implements IntegrationEvent {

    public LoggedIn(UUID userId, UUID sessionId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, sessionId, occurredAt);
    }
}
