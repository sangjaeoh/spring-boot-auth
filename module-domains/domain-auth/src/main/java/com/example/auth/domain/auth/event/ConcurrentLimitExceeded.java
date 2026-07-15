package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 동시 세션 상한 초과 통합 이벤트다. 세션 생성이 상한을 넘겨 최오래 세션이 축출된 사실을 알린다
 * (보안 알림 트리거 — 발송은 후속 단계).
 */
public record ConcurrentLimitExceeded(UUID eventId, UUID userId, List<UUID> evictedSessionIds, Instant occurredAt)
        implements IntegrationEvent {

    public ConcurrentLimitExceeded {
        evictedSessionIds = List.copyOf(evictedSessionIds);
    }

    public ConcurrentLimitExceeded(UUID userId, List<UUID> evictedSessionIds, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, evictedSessionIds, occurredAt);
    }
}
