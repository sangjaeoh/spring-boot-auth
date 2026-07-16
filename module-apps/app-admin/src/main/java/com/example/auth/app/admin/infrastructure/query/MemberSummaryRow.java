package com.example.auth.app.admin.infrastructure.query;

import com.example.auth.common.core.masking.PiiMasker;
import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.entity.LockState;
import com.example.auth.domain.user.entity.Profile;
import com.example.auth.domain.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 회원 검색 결과 행이다(격리 구역 내부 변환 — 마스킹 적용, 원문 비노출). 탈퇴 회원은 PII가 파기돼
 * 마스킹 필드가 null이다.
 */
public record MemberSummaryRow(
        UUID userId,
        @Nullable String maskedName,
        @Nullable String maskedLoginEmail,
        EffectiveMemberStatus effectiveStatus,
        Instant joinedAt) {

    /**
     * usr 회원과 auth 계정을 합성해 마스킹 적용된 검색 행을 만든다({@code account} 부재 시 잠금 없음으로
     * 간주 — 가입 원자성상 정상 데이터에선 존재).
     */
    public static MemberSummaryRow of(User user, @Nullable AuthAccount account) {
        Profile profile = user.getProfile();
        Email loginEmail = account == null ? null : account.getLoginEmail();
        LockState lockState = account == null ? LockState.NONE : account.getLockState();
        return new MemberSummaryRow(
                user.getId(),
                profile == null ? null : PiiMasker.maskName(profile.name()),
                loginEmail == null ? null : PiiMasker.maskEmail(loginEmail.value()),
                EffectiveMemberStatus.of(user.getStatus(), lockState),
                user.getCreatedAt());
    }
}
