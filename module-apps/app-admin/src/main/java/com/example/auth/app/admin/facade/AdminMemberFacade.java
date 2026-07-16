package com.example.auth.app.admin.facade;

import com.example.auth.app.admin.infrastructure.query.AdminMemberQuery;
import com.example.auth.app.admin.presentation.v1.AdminLoginAttemptResponse;
import com.example.auth.app.admin.presentation.v1.AdminMemberDetailResponse;
import com.example.auth.app.admin.presentation.v1.AdminMemberSummaryResponse;
import com.example.auth.app.admin.presentation.v1.PageResponse;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.LockReason;
import com.example.auth.domain.auth.entity.LockState;
import com.example.auth.domain.auth.event.ForceLogoutRequested;
import com.example.auth.domain.auth.info.LockReleaseInfo;
import com.example.auth.domain.auth.service.AccountLockProcessor;
import com.example.auth.domain.generic.service.AuditLogAppender;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 관리자 회원 오퍼레이션을 조율한다(조회는 격리 구역 질의, 명령은 도메인 서비스 — 파사드는 트랜잭션을
 * 열지 않는다). 모든 명령은 행위자(관리자)·전/후 값과 함께 감사 로그에 append된다.
 */
@Component
public class AdminMemberFacade {

    private final AdminMemberQuery adminMemberQuery;
    private final AccountLockProcessor accountLockProcessor;
    private final MessagePublisher messagePublisher;
    private final AuditLogAppender auditLogAppender;

    public AdminMemberFacade(
            AdminMemberQuery adminMemberQuery,
            AccountLockProcessor accountLockProcessor,
            MessagePublisher messagePublisher,
            AuditLogAppender auditLogAppender) {
        this.adminMemberQuery = adminMemberQuery;
        this.accountLockProcessor = accountLockProcessor;
        this.messagePublisher = messagePublisher;
        this.auditLogAppender = auditLogAppender;
    }

    /**
     * 로그인 이메일 정확 일치로 회원을 검색한다(마스킹 적용).
     */
    public List<AdminMemberSummaryResponse> searchByLoginEmail(String email) {
        return adminMemberQuery.searchByLoginEmail(email).stream()
                .map(AdminMemberSummaryResponse::from)
                .toList();
    }

    /**
     * 전화 blind index 동등 일치로 회원을 검색한다(마스킹 적용).
     */
    public List<AdminMemberSummaryResponse> searchByPhone(String phone) {
        return adminMemberQuery.searchByPhone(phone).stream()
                .map(AdminMemberSummaryResponse::from)
                .toList();
    }

    /**
     * 회원 상세를 반환한다(마스킹 + 실효 상태 + 역할 배정).
     */
    public AdminMemberDetailResponse getDetail(UUID userId) {
        return AdminMemberDetailResponse.from(adminMemberQuery.getDetail(userId));
    }

    /**
     * 로그인 이력을 최신순 페이지로 반환한다.
     */
    public PageResponse<AdminLoginAttemptResponse> getLoginAttempts(UUID userId, int page, int size) {
        return PageResponse.from(
                adminMemberQuery.getLoginAttempts(userId, page, size).map(AdminLoginAttemptResponse::from));
    }

    /**
     * 강제 로그아웃을 발행한다 — 소비자가 전 세션을 즉시 무효화하고(기존 Access 즉시 401), 행위를 감사에
     * 남긴다.
     */
    public void forceLogout(UUID adminId, UUID userId) {
        adminMemberQuery.requireMember(userId);
        Instant now = Instant.now();
        messagePublisher.publish(new ForceLogoutRequested(userId, now));
        auditLogAppender.record(
                adminId.toString(), "admin.member.force-logout", userId.toString(), null, null, null, now);
    }

    /**
     * 계정을 관리자 잠금한다(NONE → ADMIN_LOCKED — 로그인 즉시 거부, 자동 해제 없음). 전/후 값을 감사에
     * 남긴다.
     */
    public void lock(UUID adminId, UUID userId) {
        Instant now = Instant.now();
        accountLockProcessor.lockByAdmin(userId, now);
        auditLogAppender.record(
                adminId.toString(),
                "admin.member.lock",
                userId.toString(),
                lockStateJson(LockState.NONE, null),
                lockStateJson(LockState.ADMIN_LOCKED, LockReason.ADMIN_ACTION),
                null,
                now);
    }

    /**
     * 계정 잠금을 해제한다(TEMP_LOCKED·ADMIN_LOCKED → NONE). 전/후 값을 감사에 남긴다.
     */
    public void unlock(UUID adminId, UUID userId) {
        Instant now = Instant.now();
        LockReleaseInfo released = accountLockProcessor.unlockByAdmin(userId, now);
        auditLogAppender.record(
                adminId.toString(),
                "admin.member.unlock",
                userId.toString(),
                lockStateJson(released.previousLockState(), released.previousLockReason()),
                lockStateJson(LockState.NONE, null),
                null,
                now);
    }

    private static String lockStateJson(LockState lockState, @Nullable LockReason lockReason) {
        return lockReason == null
                ? "{\"lockState\":\"%s\"}".formatted(lockState)
                : "{\"lockState\":\"%s\",\"lockReason\":\"%s\"}".formatted(lockState, lockReason);
    }
}
