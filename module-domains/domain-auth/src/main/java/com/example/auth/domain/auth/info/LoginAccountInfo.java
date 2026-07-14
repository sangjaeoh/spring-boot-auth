package com.example.auth.domain.auth.info;

import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.FailureReason;
import com.example.auth.domain.auth.entity.LifecycleStatus;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 로그인 접근 판정에 필요한 인증 계정 스냅샷이다(경계 조회 모델).
 *
 * <p>{@code loginAllowed}가 false면 {@code blockReason}에 사유가 담긴다(이력 기록용 — 클라이언트 응답은
 * 사유를 구분하지 않는다).
 */
public record LoginAccountInfo(
        UUID userId, boolean loginAllowed, @Nullable FailureReason blockReason) {

    public static LoginAccountInfo from(AuthAccount account) {
        FailureReason blockReason = account.isLoginAllowed()
                ? null
                : (account.getUserStatus() != LifecycleStatus.ACTIVE ? FailureReason.NOT_ACTIVE : FailureReason.LOCKED);
        return new LoginAccountInfo(account.getUserId(), account.isLoginAllowed(), blockReason);
    }
}
