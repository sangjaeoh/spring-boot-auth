package com.example.auth.domain.generic.info;

import com.example.auth.domain.generic.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 감사 로그 경계 조회 모델이다.
 */
public record AuditLogInfo(
        UUID id,
        String actor,
        String action,
        @Nullable String target,
        @Nullable String beforeValue,
        @Nullable String afterValue,
        @Nullable String context,
        Instant at) {

    public static AuditLogInfo from(AuditLog auditLog) {
        return new AuditLogInfo(
                auditLog.getId(),
                auditLog.getActor(),
                auditLog.getAction(),
                auditLog.getTarget(),
                auditLog.getBeforeValue(),
                auditLog.getAfterValue(),
                auditLog.getContext(),
                auditLog.getAt());
    }
}
