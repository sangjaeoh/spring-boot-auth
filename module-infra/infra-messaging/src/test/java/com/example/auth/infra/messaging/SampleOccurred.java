package com.example.auth.infra.messaging;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 정상 소비 경로 검증용 테스트 이벤트다(운영 이벤트와 동일하게 편의 생성자를 갖는 record).
 */
public record SampleOccurred(UUID eventId, String note, Instant occurredAt) implements IntegrationEvent {

    public SampleOccurred(String note) {
        this(UuidV7Generator.generate(), note, Instant.now());
    }
}
