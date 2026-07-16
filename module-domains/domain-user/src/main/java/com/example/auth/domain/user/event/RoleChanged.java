package com.example.auth.domain.user.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 역할 배정 변경 통합 이벤트다. 변경 후 역할명 전체 스냅샷을 운반하며, 인증이 소비해 토큰 클레임의
 * 원천인 {@code auth_account.roles} 투영을 갱신한다({@code occurredAt} 단조 가드로 역순 재전달 흡수).
 */
public record RoleChanged(UUID eventId, UUID userId, List<String> roles, Instant occurredAt)
        implements IntegrationEvent {

    public RoleChanged {
        roles = List.copyOf(roles);
    }

    public RoleChanged(UUID userId, List<String> roles, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, roles, occurredAt);
    }
}
