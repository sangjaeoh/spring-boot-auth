package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 리프레시 회전(토큰 재발급) 통합 이벤트다. 유예 창 내 재시도는 동일 회전의 멱등 재반환이라 발행하지
 * 않는다(감사에는 실 회전만 남는다).
 */
public record RefreshRotated(UUID eventId, UUID userId, UUID sessionId, Instant occurredAt)
        implements IntegrationEvent {

    public RefreshRotated(UUID userId, UUID sessionId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, sessionId, occurredAt);
    }
}
