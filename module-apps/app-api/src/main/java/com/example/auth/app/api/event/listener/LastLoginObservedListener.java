package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.LastLoginObserved;
import com.example.auth.domain.user.service.UserModifier;
import org.springframework.stereotype.Component;

/**
 * 로그인 관측을 소비해 회원의 {@code lastLoginAt}을 갱신한다(휴면 판정 기산점 — 유저는 세션 저장소를
 * 직접 조회하지 않는다). 단조 갱신이라 재전달·역순 도착이 멱등 흡수된다.
 */
@Component
public class LastLoginObservedListener implements IntegrationEventConsumer<LastLoginObserved> {

    private final UserModifier userModifier;

    public LastLoginObservedListener(UserModifier userModifier) {
        this.userModifier = userModifier;
    }

    @Override
    public String consumerId() {
        return "user-account.last-login-observed";
    }

    @Override
    public Class<LastLoginObserved> eventType() {
        return LastLoginObserved.class;
    }

    @Override
    public void consume(LastLoginObserved event) {
        userModifier.recordLogin(event.userId(), event.occurredAt());
    }
}
