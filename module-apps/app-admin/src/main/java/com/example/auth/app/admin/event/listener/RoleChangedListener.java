package com.example.auth.app.admin.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.service.AuthAccountModifier;
import com.example.auth.domain.user.event.RoleChanged;
import org.springframework.stereotype.Component;

/**
 * 역할 배정 변경을 소비해 토큰 클레임의 원천인 인증 {@code roles} 투영을 갱신한다({@code occurredAt}
 * 단조 가드 멱등). 반영 수준은 재발급 시 반영이다 — 기존 Access는 TTL까지 이전 역할을 유지하고, 즉시
 * 차단이 필요하면 관리자가 강제 로그아웃을 병행한다.
 */
@Component
public class RoleChangedListener implements IntegrationEventConsumer<RoleChanged> {

    private final AuthAccountModifier authAccountModifier;

    public RoleChangedListener(AuthAccountModifier authAccountModifier) {
        this.authAccountModifier = authAccountModifier;
    }

    @Override
    public String consumerId() {
        return "auth-account.role-changed";
    }

    @Override
    public Class<RoleChanged> eventType() {
        return RoleChanged.class;
    }

    @Override
    public void consume(RoleChanged event) {
        authAccountModifier.applyRoles(event.userId(), event.roles(), event.occurredAt());
    }
}
