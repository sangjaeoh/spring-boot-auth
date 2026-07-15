package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.entity.LifecycleStatus;
import com.example.auth.domain.auth.service.AuthAccountModifier;
import com.example.auth.domain.auth.service.DeviceRemover;
import com.example.auth.domain.auth.service.PasswordCredentialRemover;
import com.example.auth.domain.auth.service.SessionProcessor;
import com.example.auth.domain.auth.service.SocialConnectionRemover;
import com.example.auth.domain.user.event.UserWithdrawn;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴를 소비해 인증 측을 정리한다 — 상태 스냅샷(WITHDRAWN·loginEmail 파기) + 전 세션 무효화 +
 * 자격증명/소셜/기기 파기. 전 단계가 멱등이라 재전달·파사드 동기 선행과의 중복 수행이 무해하고, 실패는
 * DLQ 재시도로 유실 없이 수렴한다.
 */
@Component
public class UserWithdrawnListener implements IntegrationEventConsumer<UserWithdrawn> {

    private final AuthAccountModifier authAccountModifier;
    private final SessionProcessor sessionProcessor;
    private final PasswordCredentialRemover passwordCredentialRemover;
    private final SocialConnectionRemover socialConnectionRemover;
    private final DeviceRemover deviceRemover;

    public UserWithdrawnListener(
            AuthAccountModifier authAccountModifier,
            SessionProcessor sessionProcessor,
            PasswordCredentialRemover passwordCredentialRemover,
            SocialConnectionRemover socialConnectionRemover,
            DeviceRemover deviceRemover) {
        this.authAccountModifier = authAccountModifier;
        this.sessionProcessor = sessionProcessor;
        this.passwordCredentialRemover = passwordCredentialRemover;
        this.socialConnectionRemover = socialConnectionRemover;
        this.deviceRemover = deviceRemover;
    }

    @Override
    public String consumerId() {
        return "auth-account.user-withdrawn";
    }

    @Override
    public Class<UserWithdrawn> eventType() {
        return UserWithdrawn.class;
    }

    @Override
    public void consume(UserWithdrawn event) {
        authAccountModifier.applyUserStatus(event.userId(), LifecycleStatus.WITHDRAWN, event.statusVersion());
        sessionProcessor.revokeAll(event.userId());
        passwordCredentialRemover.purge(event.userId());
        socialConnectionRemover.purgeAll(event.userId());
        deviceRemover.purgeAll(event.userId());
    }
}
