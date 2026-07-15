package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 관리자 강제 로그아웃 명령 통합 이벤트다. 인증이 소비해 사용자의 전 세션을 즉시 무효화한다
 * (DOMAIN_MODEL §8.3 — 발행측 관리자 콘솔은 후속 단계).
 */
public record ForceLogoutRequested(UUID eventId, UUID userId, Instant occurredAt) implements IntegrationEvent {

    public ForceLogoutRequested(UUID userId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, occurredAt);
    }
}
