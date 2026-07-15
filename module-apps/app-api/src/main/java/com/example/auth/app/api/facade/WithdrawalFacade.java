package com.example.auth.app.api.facade;

import com.example.auth.domain.auth.entity.LifecycleStatus;
import com.example.auth.domain.auth.service.AuthAccountModifier;
import com.example.auth.domain.auth.service.SessionProcessor;
import com.example.auth.domain.user.service.UserWithdrawalProcessor;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴를 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지 않는다).
 *
 * <p>유저 탈퇴 커밋 직후 {@code UserWithdrawn} 소비가 같은 스레드에서 인증 정리(스냅샷·세션·자격증명)를
 * 수행하지만, 소비 실패는 DLQ 재시도로 미뤄지므로 신규 로그인 창을 닫는 두 동작(스냅샷 WITHDRAWN 플립 +
 * 세션 전멸)은 여기서 동기로도 수행한다 — 멱등이라 이중 수행은 무해하고, 실패는 응답으로 드러난다
 * (fail-closed: 이 API가 성공(204)했다면 파기된 정체성으로 fresh 세션이 발급되지 않는다).
 */
@Component
public class WithdrawalFacade {

    private final UserWithdrawalProcessor userWithdrawalProcessor;
    private final AuthAccountModifier authAccountModifier;
    private final SessionProcessor sessionProcessor;

    public WithdrawalFacade(
            UserWithdrawalProcessor userWithdrawalProcessor,
            AuthAccountModifier authAccountModifier,
            SessionProcessor sessionProcessor) {
        this.userWithdrawalProcessor = userWithdrawalProcessor;
        this.authAccountModifier = authAccountModifier;
        this.sessionProcessor = sessionProcessor;
    }

    /**
     * 회원을 탈퇴 처리한다 — PII 즉시 파기·CI tombstone(유저) + 로그인 차단·전 세션 무효화(인증 동기) +
     * 자격증명/소셜/기기 정리(이벤트 소비, DLQ 내구).
     */
    public void withdraw(UUID userId) {
        long statusVersion = userWithdrawalProcessor.withdraw(userId);
        authAccountModifier.applyUserStatus(userId, LifecycleStatus.WITHDRAWN, statusVersion);
        sessionProcessor.revokeAll(userId);
    }
}
