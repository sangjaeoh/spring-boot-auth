package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.entity.LifecycleStatus;
import com.example.auth.domain.auth.service.AuthAccountModifier;
import com.example.auth.domain.user.event.UserStatusChanged;
import org.springframework.stereotype.Component;

/**
 * 회원 생명주기 변경(휴면 전환·해제)을 소비해 인증-로컬 {@code userStatus} 스냅샷을 반영한다.
 * 단조 버전 가드로 역순·중복 이벤트를 멱등 흡수한다(유저가 유일 writer — 인증은 투영만).
 */
@Component
public class UserStatusChangedListener implements IntegrationEventConsumer<UserStatusChanged> {

    private final AuthAccountModifier authAccountModifier;

    public UserStatusChangedListener(AuthAccountModifier authAccountModifier) {
        this.authAccountModifier = authAccountModifier;
    }

    @Override
    public String consumerId() {
        return "auth-account.user-status-changed";
    }

    @Override
    public Class<UserStatusChanged> eventType() {
        return UserStatusChanged.class;
    }

    @Override
    public void consume(UserStatusChanged event) {
        authAccountModifier.applyUserStatus(
                event.userId(), LifecycleStatus.valueOf(event.status().name()), event.statusVersion());
    }
}
