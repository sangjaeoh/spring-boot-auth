package com.example.auth.app.admin.presentation.v1;

import com.example.auth.app.admin.facade.AdminMemberFacade;
import com.example.auth.app.admin.facade.AdminRoleFacade;
import com.example.auth.common.web.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 회원 API(검색·상세·로그인 이력·강제 로그아웃·잠금/해제·권한 변경).
 *
 * <p>접근 통제는 보안 체인(ADMIN·SUPER_ADMIN)과 메서드 가드(권한 변경은 SUPER_ADMIN)가 집행한다.
 */
@RestController
@RequestMapping("/admin/members")
public class AdminMemberController {

    private final AdminMemberFacade adminMemberFacade;
    private final AdminRoleFacade adminRoleFacade;

    public AdminMemberController(AdminMemberFacade adminMemberFacade, AdminRoleFacade adminRoleFacade) {
        this.adminMemberFacade = adminMemberFacade;
        this.adminRoleFacade = adminRoleFacade;
    }

    /**
     * 로그인 이메일 또는 전화(blind index)로 회원을 검색한다(마스킹 적용 — 조건 미제시는 400).
     */
    @GetMapping
    public List<AdminMemberSummaryResponse> search(
            @RequestParam(required = false) @Nullable String email,
            @RequestParam(required = false) @Nullable String phone) {
        if (email != null) {
            return adminMemberFacade.searchByLoginEmail(email);
        }
        if (phone != null) {
            return adminMemberFacade.searchByPhone(phone);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email 또는 phone 검색 조건이 필요합니다.");
    }

    /**
     * 회원 상세를 반환한다(마스킹 + 실효 상태 + 역할 배정).
     */
    @GetMapping("/{userId}")
    public AdminMemberDetailResponse detail(@PathVariable UUID userId) {
        return adminMemberFacade.getDetail(userId);
    }

    /**
     * 로그인 이력을 최신순 페이지로 반환한다.
     */
    @GetMapping("/{userId}/login-attempts")
    public PageResponse<AdminLoginAttemptResponse> loginAttempts(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminMemberFacade.getLoginAttempts(userId, page, size);
    }

    /**
     * 회원의 전 세션을 강제 로그아웃한다(기존 Access 즉시 401).
     */
    @PostMapping("/{userId}/force-logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forceLogout(@AuthenticationPrincipal AuthUser admin, @PathVariable UUID userId) {
        adminMemberFacade.forceLogout(admin.userId(), userId);
    }

    /**
     * 계정을 관리자 잠금한다(ADMIN_LOCKED — 자동 해제 없음, 이미 잠금이면 409).
     */
    @PostMapping("/{userId}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lock(@AuthenticationPrincipal AuthUser admin, @PathVariable UUID userId) {
        adminMemberFacade.lock(admin.userId(), userId);
    }

    /**
     * 계정 잠금을 해제한다(잠금이 아니면 409).
     */
    @DeleteMapping("/{userId}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@AuthenticationPrincipal AuthUser admin, @PathVariable UUID userId) {
        adminMemberFacade.unlock(admin.userId(), userId);
    }

    /**
     * 역할을 배정한다(SUPER_ADMIN 전용 — 중복 배정 409).
     */
    @PostMapping("/{userId}/roles")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRole(
            @AuthenticationPrincipal AuthUser admin,
            @PathVariable UUID userId,
            @Valid @RequestBody RoleAssignRequest request) {
        adminRoleFacade.assignRole(admin.userId(), userId, request.role());
    }

    /**
     * 역할 배정을 해제한다(SUPER_ADMIN 전용 — 미배정 404).
     */
    @DeleteMapping("/{userId}/roles/{role}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeRole(
            @AuthenticationPrincipal AuthUser admin, @PathVariable UUID userId, @PathVariable String role) {
        adminRoleFacade.revokeRole(admin.userId(), userId, role);
    }
}
