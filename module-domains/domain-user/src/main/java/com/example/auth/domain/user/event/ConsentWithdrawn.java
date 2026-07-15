package com.example.auth.domain.user.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.domain.user.entity.TermsType;
import java.time.Instant;
import java.util.UUID;

/**
 * 이용 중 동의 철회 통합 이벤트다(선택 약관만 — 필수 철회는 탈퇴 경로). 마케팅(MARKETING) 철회는 알림
 * 수신 설정의 마케팅 매트릭스 동기화가 소비한다.
 */
public record ConsentWithdrawn(UUID eventId, UUID userId, TermsType termsType, Instant occurredAt)
        implements IntegrationEvent {

    public ConsentWithdrawn(UUID userId, TermsType termsType, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, termsType, occurredAt);
    }
}
