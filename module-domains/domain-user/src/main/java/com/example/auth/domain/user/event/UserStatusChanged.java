package com.example.auth.domain.user.event;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.domain.user.entity.LifecycleStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * 회원 생명주기 상태 변경 통합 이벤트다(휴면 전환·해제). 인증이 소비해 {@code userStatus} 스냅샷을
 * 단조 버전({@code statusVersion}) 기준 멱등 갱신한다. 탈퇴는 정리 의무가 결합된 {@code UserWithdrawn}이
 * 별도로 소유한다.
 */
public record UserStatusChanged(
        UUID eventId, UUID userId, LifecycleStatus status, long statusVersion, Instant occurredAt)
        implements IntegrationEvent {

    public UserStatusChanged(UUID userId, LifecycleStatus status, long statusVersion, Instant occurredAt) {
        this(UuidV7Generator.generate(), userId, status, statusVersion, occurredAt);
    }
}
