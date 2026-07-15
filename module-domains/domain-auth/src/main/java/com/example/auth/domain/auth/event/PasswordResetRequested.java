package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 비밀번호 재설정이 요청된 통합 이벤트다(인증코드 발급 시점).
 *
 * <p>보안 알림·감사의 트리거가 된다(소비는 후속 단계).
 */
public record PasswordResetRequested(UUID eventId, UUID userId, Instant occurredAt) implements IntegrationEvent {

    public PasswordResetRequested(UUID userId, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, occurredAt);
    }
}
