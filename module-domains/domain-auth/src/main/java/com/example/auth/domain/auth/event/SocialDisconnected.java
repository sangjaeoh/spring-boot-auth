package com.example.auth.domain.auth.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.domain.auth.entity.SocialProvider;
import java.time.Instant;
import java.util.UUID;

/**
 * 소셜 로그인 수단이 해제된 통합 이벤트다.
 */
public record SocialDisconnected(UUID eventId, UUID userId, SocialProvider provider, Instant occurredAt)
        implements IntegrationEvent {

    public SocialDisconnected(UUID userId, SocialProvider provider, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, provider, occurredAt);
    }
}
