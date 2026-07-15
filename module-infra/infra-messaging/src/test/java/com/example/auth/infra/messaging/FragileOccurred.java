package com.example.auth.infra.messaging;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 소비 실패·DLQ 재시도 경로 검증용 테스트 이벤트다(직렬화 왕복 대상).
 */
public record FragileOccurred(UUID eventId, String note, Instant occurredAt) implements IntegrationEvent {

    public FragileOccurred(String note) {
        this(UuidV7Generator.generate(), note, Instant.now());
    }
}
