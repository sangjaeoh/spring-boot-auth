package com.example.auth.app.admin.facade;

import com.example.auth.app.admin.presentation.v1.AuditLogResponse;
import com.example.auth.app.admin.presentation.v1.PageResponse;
import com.example.auth.domain.generic.service.AuditLogReader;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 감사 로그 조회를 조율한다.
 */
@Component
public class AdminAuditFacade {

    private final AuditLogReader auditLogReader;

    public AdminAuditFacade(AuditLogReader auditLogReader) {
        this.auditLogReader = auditLogReader;
    }

    /**
     * 감사 기록을 발생 시각 내림차순 페이지로 반환한다({@code target} 필터 옵션).
     */
    public PageResponse<AuditLogResponse> getPage(@Nullable String target, int page, int size) {
        return PageResponse.from(
                auditLogReader.getPage(target, PageRequest.of(page, size)).map(AuditLogResponse::from));
    }
}
