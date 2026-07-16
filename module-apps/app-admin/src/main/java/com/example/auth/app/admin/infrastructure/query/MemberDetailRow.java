package com.example.auth.app.admin.infrastructure.query;

import com.example.auth.common.core.masking.PiiMasker;
import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.entity.LockReason;
import com.example.auth.domain.auth.entity.LockState;
import com.example.auth.domain.user.entity.Contact;
import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.Profile;
import com.example.auth.domain.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 회원 상세 행이다(격리 구역 내부 변환 — 마스킹 적용, 원문 비노출). 생명주기·잠금 두 축과 실효 상태,
 * usr 정본의 역할 배정을 함께 나른다.
 */
public record MemberDetailRow(
        UUID userId,
        @Nullable String maskedName,
        @Nullable String maskedContactEmail,
        @Nullable String maskedContactPhone,
        @Nullable String maskedLoginEmail,
        LifecycleStatus lifecycleStatus,
        LockState lockState,
        @Nullable LockReason lockReason,
        @Nullable Instant lockedAt,
        EffectiveMemberStatus effectiveStatus,
        List<String> roles,
        @Nullable Instant lastLoginAt,
        @Nullable Instant dormantAt,
        @Nullable Instant withdrawnAt,
        Instant joinedAt) {

    public MemberDetailRow {
        roles = List.copyOf(roles);
    }

    /**
     * usr 회원·auth 계정·역할 배정을 합성해 마스킹 적용된 상세 행을 만든다.
     */
    public static MemberDetailRow of(User user, AuthAccount account, List<String> roles) {
        Profile profile = user.getProfile();
        Contact contact = user.getContact();
        Email loginEmail = account.getLoginEmail();
        return new MemberDetailRow(
                user.getId(),
                profile == null ? null : PiiMasker.maskName(profile.name()),
                contact == null
                        ? null
                        : PiiMasker.maskEmail(contact.contactEmail().value()),
                contact == null
                        ? null
                        : PiiMasker.maskPhone(contact.contactPhone().number()),
                loginEmail == null ? null : PiiMasker.maskEmail(loginEmail.value()),
                user.getStatus(),
                account.getLockState(),
                account.getLockReason(),
                account.getLockedAt(),
                EffectiveMemberStatus.of(user.getStatus(), account.getLockState()),
                roles,
                user.getLastLoginAt(),
                user.getDormantAt(),
                user.getWithdrawnAt(),
                user.getCreatedAt());
    }
}
