package com.example.auth.app.admin.facade;

import com.example.auth.domain.generic.service.AuditLogAppender;
import com.example.auth.domain.user.entity.RoleName;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.info.RoleAssignmentInfo;
import com.example.auth.domain.user.service.RoleAssignmentProcessor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 관리자 권한 변경(역할 배정·해제)을 조율한다 — 변경 전/후 역할 스냅샷을 감사에 남기고,
 * {@code RoleChanged} 발행(도메인 소유)이 인증 클레임 투영을 갱신시킨다.
 */
@Component
public class AdminRoleFacade {

    private final RoleAssignmentProcessor roleAssignmentProcessor;
    private final AuditLogAppender auditLogAppender;

    public AdminRoleFacade(RoleAssignmentProcessor roleAssignmentProcessor, AuditLogAppender auditLogAppender) {
        this.roleAssignmentProcessor = roleAssignmentProcessor;
        this.auditLogAppender = auditLogAppender;
    }

    /**
     * 역할을 배정한다(중복 배정 409). 전/후 역할 스냅샷을 감사에 남긴다.
     */
    public void assignRole(UUID adminId, UUID userId, String roleName) {
        Instant now = Instant.now();
        RoleAssignmentInfo changed = roleAssignmentProcessor.assignRole(userId, parseRole(roleName), now);
        auditLogAppender.record(
                adminId.toString(),
                "admin.member.role-assign",
                userId.toString(),
                rolesJson(changed.beforeRoles()),
                rolesJson(changed.afterRoles()),
                null,
                now);
    }

    /**
     * 역할 배정을 해제한다(미배정 404). 전/후 역할 스냅샷을 감사에 남긴다.
     */
    public void revokeRole(UUID adminId, UUID userId, String roleName) {
        Instant now = Instant.now();
        RoleAssignmentInfo changed = roleAssignmentProcessor.revokeRole(userId, parseRole(roleName), now);
        auditLogAppender.record(
                adminId.toString(),
                "admin.member.role-revoke",
                userId.toString(),
                rolesJson(changed.beforeRoles()),
                rolesJson(changed.afterRoles()),
                null,
                now);
    }

    private static RoleName parseRole(String roleName) {
        try {
            return RoleName.valueOf(roleName);
        } catch (IllegalArgumentException unknownRole) {
            throw new UserException(UserErrorCode.ROLE_NOT_FOUND);
        }
    }

    private static String rolesJson(List<String> roles) {
        // 역할명은 enum 상수라 이스케이프가 필요 없다.
        return roles.stream().map(role -> "\"" + role + "\"").collect(Collectors.joining(",", "{\"roles\":[", "]}"));
    }
}
