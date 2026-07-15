package com.example.auth.app.batch.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.entity.LifecycleStatus;
import com.example.auth.domain.auth.service.AuthAccountModifier;
import com.example.auth.domain.user.event.UserStatusChanged;
import org.springframework.stereotype.Component;

/**
 * 배치가 발행한 생명주기 변경(휴면 전환)을 같은 프로세스에서 소비해 인증-로컬 {@code userStatus}
 * 스냅샷을 반영한다 — in-process 전달이라 발행 프로세스마다 소비자가 있어야 하며, 단조 버전 가드로
 * 역순·중복이 멱등 흡수된다(디둡 원장·DLQ는 msg 스키마 공유).
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
