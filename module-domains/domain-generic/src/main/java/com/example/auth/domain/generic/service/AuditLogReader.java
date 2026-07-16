package com.example.auth.domain.generic.service;

import com.example.auth.domain.generic.entity.AuditLog;
import com.example.auth.domain.generic.info.AuditLogInfo;
import com.example.auth.domain.generic.repository.AuditLogRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 조회를 담당한다.
 */
@Service
public class AuditLogReader {

    private final AuditLogRepository repository;

    public AuditLogReader(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * 감사 기록을 발생 시각 내림차순으로 페이지 조회한다. {@code target}이 주어지면 대상 필터를 적용한다.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogInfo> getPage(@Nullable String target, Pageable pageable) {
        Page<AuditLog> page = target == null
                ? repository.findAllByOrderByAtDesc(pageable)
                : repository.findByTargetOrderByAtDesc(target, pageable);
        return page.map(AuditLogInfo::from);
    }
}
