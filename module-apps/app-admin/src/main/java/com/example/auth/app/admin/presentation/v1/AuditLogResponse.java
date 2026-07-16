package com.example.auth.app.admin.presentation.v1;

import com.example.auth.domain.generic.info.AuditLogInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 감사 로그 응답이다(전/후 값은 기록된 JSON 문자열 그대로 노출).
 */
public record AuditLogResponse(
        UUID id,
        String actor,
        String action,
        @Nullable String target,
        @Nullable String beforeValue,
        @Nullable String afterValue,
        @Nullable String context,
        Instant at) {

    public static AuditLogResponse from(AuditLogInfo info) {
        return new AuditLogResponse(
                info.id(),
                info.actor(),
                info.action(),
                info.target(),
                info.beforeValue(),
                info.afterValue(),
                info.context(),
                info.at());
    }
}
