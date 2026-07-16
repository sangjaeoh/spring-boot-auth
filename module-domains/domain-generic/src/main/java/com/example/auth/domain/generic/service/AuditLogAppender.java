package com.example.auth.domain.generic.service;

import com.example.auth.domain.generic.entity.AuditLog;
import com.example.auth.domain.generic.repository.AuditLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 기록 append를 담당한다 — 통합 이벤트 구독 소비와 관리자 파사드가 공용하는 유일한 쓰기 경로다.
 */
@Service
public class AuditLogAppender {

    private final AuditLogRepository repository;

    public AuditLogAppender(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * 감사 기록을 append하고 새 ID를 반환한다(append 후 수정 경로 없음 — WORM).
     */
    @Transactional
    public UUID record(
            String actor,
            String action,
            @Nullable String target,
            @Nullable String beforeValue,
            @Nullable String afterValue,
            @Nullable String context,
            Instant at) {
        AuditLog auditLog = AuditLog.create(actor, action, target, beforeValue, afterValue, context, at);
        repository.save(auditLog);
        return auditLog.getId();
    }
}
