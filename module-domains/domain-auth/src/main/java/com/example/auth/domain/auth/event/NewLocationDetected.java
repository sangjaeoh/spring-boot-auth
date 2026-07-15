package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 신규 지역 로그인 감지 통합 이벤트다 — 최근 성공 이력에 없던 국가에서의 로그인. 알림(보안)이
 * 구독한다.
 */
public record NewLocationDetected(UUID eventId, UUID userId, String countryCode, Instant occurredAt)
        implements IntegrationEvent {

    public NewLocationDetected(UUID userId, String countryCode, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, countryCode, occurredAt);
    }
}
