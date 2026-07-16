package com.example.auth.app.admin.presentation.v1;

import com.example.auth.app.admin.infrastructure.query.MemberDetailRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 회원 상세 응답이다 — 마스킹 적용 PII, 생명주기·잠금 두 축과 실효 상태({@code effectiveStatus} =
 * f(lifecycle, lock)), usr 정본의 역할 배정을 함께 노출한다.
 */
public record AdminMemberDetailResponse(
        UUID userId,
        @Nullable String maskedName,
        @Nullable String maskedContactEmail,
        @Nullable String maskedContactPhone,
        @Nullable String maskedLoginEmail,
        String lifecycleStatus,
        String lockState,
        @Nullable String lockReason,
        @Nullable Instant lockedAt,
        String effectiveStatus,
        List<String> roles,
        @Nullable Instant lastLoginAt,
        @Nullable Instant dormantAt,
        @Nullable Instant withdrawnAt,
        Instant joinedAt) {

    public AdminMemberDetailResponse {
        roles = List.copyOf(roles);
    }

    public static AdminMemberDetailResponse from(MemberDetailRow row) {
        return new AdminMemberDetailResponse(
                row.userId(),
                row.maskedName(),
                row.maskedContactEmail(),
                row.maskedContactPhone(),
                row.maskedLoginEmail(),
                row.lifecycleStatus().name(),
                row.lockState().name(),
                row.lockReason() == null ? null : row.lockReason().name(),
                row.lockedAt(),
                row.effectiveStatus().name(),
                row.roles(),
                row.lastLoginAt(),
                row.dormantAt(),
                row.withdrawnAt(),
                row.joinedAt());
    }
}
