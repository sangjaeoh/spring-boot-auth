package com.example.auth.app.admin.presentation.v1;

import com.example.auth.app.admin.facade.AdminAuditFacade;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 로그 조회 API.
 */
@RestController
@RequestMapping("/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditFacade adminAuditFacade;

    public AdminAuditLogController(AdminAuditFacade adminAuditFacade) {
        this.adminAuditFacade = adminAuditFacade;
    }

    /**
     * 감사 기록을 발생 시각 내림차순 페이지로 반환한다({@code target} 필터 옵션 — 대상 식별자 정확 일치).
     */
    @GetMapping
    public PageResponse<AuditLogResponse> page(
            @RequestParam(required = false) @Nullable String target,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminAuditFacade.getPage(target, page, size);
    }
}
