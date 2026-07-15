package com.example.auth.domain.user.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * 회원 탈퇴 통합 이벤트다. 인증이 소비해 세션 전멸·자격증명/소셜/기기 정리·상태 스냅샷(WITHDRAWN)
 * 반영을 수행한다. {@code statusVersion}은 스냅샷 순서 역전 방지용 단조 버전이다.
 */
public record UserWithdrawn(UUID eventId, UUID userId, long statusVersion, Instant occurredAt)
        implements IntegrationEvent {

    public UserWithdrawn(UUID userId, long statusVersion, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, statusVersion, occurredAt);
    }
}
