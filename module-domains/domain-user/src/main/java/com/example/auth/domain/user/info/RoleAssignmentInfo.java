package com.example.auth.domain.user.info;

import java.util.List;

/**
 * 역할 배정 변경의 전/후 역할명 스냅샷이다(감사 기록용 경계 조회 모델).
 */
public record RoleAssignmentInfo(List<String> beforeRoles, List<String> afterRoles) {

    public RoleAssignmentInfo {
        beforeRoles = List.copyOf(beforeRoles);
        afterRoles = List.copyOf(afterRoles);
    }
}
