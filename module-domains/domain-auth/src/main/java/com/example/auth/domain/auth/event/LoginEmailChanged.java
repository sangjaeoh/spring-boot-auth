package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 식별자 변경 통합 이벤트다(보안 알림·감사 대상 — 계정 탈취 시 사용자 인지 수단).
 */
public record LoginEmailChanged(UUID eventId, UUID userId, Instant occurredAt) implements IntegrationEvent {

    public LoginEmailChanged(UUID userId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, occurredAt);
    }
}
