package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 관측 통합 이벤트다(세션 발급 = 접속). 유저가 소비해 {@code lastLoginAt}을 갱신한다 —
 * 휴면 판정은 유저가 세션 저장소를 직접 조회하지 않고 이 이벤트로만 접속을 인지한다.
 */
public record LastLoginObserved(UUID eventId, UUID userId, Instant occurredAt) implements IntegrationEvent {

    public LastLoginObserved(UUID userId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, occurredAt);
    }
}
