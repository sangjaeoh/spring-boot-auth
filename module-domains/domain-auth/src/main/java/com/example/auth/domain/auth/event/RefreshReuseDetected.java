package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 리프레시 토큰 재사용(탈취) 감지 통합 이벤트다.
 *
 * <p>세션 패밀리는 이미 무효화되었으며, 보안 알림 발송의 트리거가 된다(소비는 후속 단계).
 */
public record RefreshReuseDetected(UUID eventId, UUID userId, UUID sessionId, Instant occurredAt)
        implements IntegrationEvent {

    public RefreshReuseDetected(UUID userId, UUID sessionId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, sessionId, occurredAt);
    }
}
