package com.example.auth.app.admin.presentation.v1;

import com.example.auth.app.admin.infrastructure.query.MemberSummaryRow;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 회원 검색 결과 응답이다(마스킹 적용 — 원문 비노출). 탈퇴 회원은 PII 파기로 마스킹 필드가 null이다.
 */
public record AdminMemberSummaryResponse(
        UUID userId,
        @Nullable String maskedName,
        @Nullable String maskedLoginEmail,
        String effectiveStatus,
        Instant joinedAt) {

    public static AdminMemberSummaryResponse from(MemberSummaryRow row) {
        return new AdminMemberSummaryResponse(
                row.userId(),
                row.maskedName(),
                row.maskedLoginEmail(),
                row.effectiveStatus().name(),
                row.joinedAt());
    }
}
