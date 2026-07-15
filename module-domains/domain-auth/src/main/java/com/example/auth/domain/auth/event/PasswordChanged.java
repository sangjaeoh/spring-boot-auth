package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 상태에서 비밀번호가 변경된 통합 이벤트다.
 *
 * <p>보안 카테고리 알림·감사의 트리거가 된다(소비는 후속 단계).
 */
public record PasswordChanged(UUID eventId, UUID userId, Instant occurredAt) implements IntegrationEvent {

    public PasswordChanged(UUID userId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, occurredAt);
    }
}
