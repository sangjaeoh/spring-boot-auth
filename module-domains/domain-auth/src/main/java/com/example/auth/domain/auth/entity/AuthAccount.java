package com.example.auth.domain.auth.entity;

import com.example.auth.common.jpa.entity.BaseTimeEntity;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 인증 계정 애그리거트 루트다. {@code userId}로 회원과 1:1 연결된다.
 *
 * <p>PK는 유저 서비스가 채번한 {@code UserId}를 그대로 쓴다({@code @GeneratedValue} 없음). 로그인 핫패스
 * 접근 판정은 유저 동기 호출 없이 로컬 스냅샷({@code userStatus}·{@code lockState})으로만 한다.
 * {@code userStatus}는 유저가 유일 writer인 읽기전용 투영이며 {@code applyUserStatus}가 단조
 * {@code userStatusVersion}으로 순서 역전 없이 멱등 반영한다. 탈퇴 반영 시 {@code loginEmail}을
 * 파기(null)해 식별자를 해제한다 — 유니크 인덱스는 null을 제외하므로 재가입이 같은 이메일을 쓸 수 있다.
 * 잠금 오버레이({@code lockState})는 생명주기와 직교하며 일시 잠금은 쿨다운 경과 시 해제된다.
 * {@code roles}는 유저의 역할 배정 투영(토큰 클레임의 원천)으로 {@code RoleChanged} 소비
 * ({@code applyRoles})로만 갱신된다.
 */
@Entity
@Table(schema = "auth", name = "auth_account")
public class AuthAccount extends BaseTimeEntity<UUID> {

    private static final String DEFAULT_ROLE = "USER";
    private static final String ROLE_DELIMITER = ",";

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Convert(converter = EmailConverter.class)
    @Column(name = "login_email", length = 320)
    @Nullable
    private Email loginEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_state", length = 20)
    private LockState lockState;

    @Column(name = "locked_at")
    @Nullable
    private Instant lockedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_reason", length = 40)
    @Nullable
    private LockReason lockReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", length = 20)
    private LifecycleStatus userStatus;

    @Column(name = "user_status_version")
    private long userStatusVersion;

    @Column(name = "roles", length = 200)
    private String roles;

    @Column(name = "roles_applied_at")
    @Nullable
    private Instant rolesAppliedAt;

    protected AuthAccount() {}

    private AuthAccount(UUID userId, Email loginEmail) {
        this.userId = userId;
        this.loginEmail = loginEmail;
        this.lockState = LockState.NONE;
        this.userStatus = LifecycleStatus.ACTIVE;
        this.userStatusVersion = 0;
        this.roles = DEFAULT_ROLE;
    }

    /**
     * 유저 서비스가 채번한 {@code userId}로 활성·미잠금 인증 계정을 생성한다.
     */
    public static AuthAccount create(UUID userId, String loginEmail) {
        return new AuthAccount(userId, Email.of(loginEmail));
    }

    /**
     * 접근 판정이 허용되는지 반환한다: 생명주기 ACTIVE ∧ 잠금 NONE.
     */
    public boolean isLoginAllowed() {
        return userStatus == LifecycleStatus.ACTIVE && lockState == LockState.NONE;
    }

    /**
     * 연속 로그인 실패로 일시 잠금한다(NONE → TEMP_LOCKED).
     */
    public void lockTemporarily(LockReason reason, Instant at) {
        if (lockState != LockState.NONE) {
            throw new IllegalStateException("NONE 상태만 일시 잠금할 수 있다: " + lockState);
        }
        this.lockState = LockState.TEMP_LOCKED;
        this.lockReason = reason;
        this.lockedAt = at;
    }

    /**
     * 관리자 명령으로 잠금한다(NONE → ADMIN_LOCKED). 쿨다운 자동 해제 대상이 아니며 관리자 해제만
     * 가능하다.
     *
     * @throws AuthException 이미 잠긴 계정이면(409)
     */
    public void lockByAdmin(Instant at) {
        if (lockState != LockState.NONE) {
            throw new AuthException(AuthErrorCode.ACCOUNT_ALREADY_LOCKED);
        }
        this.lockState = LockState.ADMIN_LOCKED;
        this.lockReason = LockReason.ADMIN_ACTION;
        this.lockedAt = at;
    }

    /**
     * 관리자 명령으로 잠금을 해제한다(TEMP_LOCKED·ADMIN_LOCKED → NONE).
     *
     * @throws AuthException 잠기지 않은 계정이면(409)
     */
    public void releaseLockByAdmin() {
        if (lockState == LockState.NONE) {
            throw new AuthException(AuthErrorCode.ACCOUNT_NOT_LOCKED);
        }
        this.lockState = LockState.NONE;
        this.lockReason = null;
        this.lockedAt = null;
    }

    /**
     * 일시 잠금을 해제한다(TEMP_LOCKED → NONE) — 쿨다운 경과 자동 해제 경로.
     */
    public void releaseTemporaryLock() {
        if (lockState != LockState.TEMP_LOCKED) {
            throw new IllegalStateException("TEMP_LOCKED 상태만 해제할 수 있다: " + lockState);
        }
        this.lockState = LockState.NONE;
        this.lockReason = null;
        this.lockedAt = null;
    }

    /**
     * 일시 잠금의 쿨다운이 경과했는지 반환한다(관리자 잠금은 항상 false — 관리자 해제 필요).
     */
    public boolean isTemporaryLockExpired(Instant now, Duration cooldown) {
        return lockState == LockState.TEMP_LOCKED
                && lockedAt != null
                && !lockedAt.plus(cooldown).isAfter(now);
    }

    /**
     * 로그인 식별자를 변경한다(연락용 {@code contactEmail}과 독립). 유일성은 유니크 인덱스가 backstop한다.
     */
    public void changeLoginEmail(Email newEmail) {
        this.loginEmail = newEmail;
    }

    /**
     * 유저의 역할 배정 스냅샷을 반영한다 — 제시 시각이 마지막 반영 시각 이후일 때만 갱신하고(역순 재전달
     * 흡수·멱등), 반영 여부를 반환한다. 토큰 roles 클레임은 이 투영에서 발급 시점에 채워진다.
     */
    public boolean applyRoles(List<String> roleNames, Instant occurredAt) {
        if (rolesAppliedAt != null && !occurredAt.isAfter(rolesAppliedAt)) {
            return false;
        }
        this.roles = String.join(ROLE_DELIMITER, roleNames);
        this.rolesAppliedAt = occurredAt;
        return true;
    }

    /**
     * 유저 생명주기 스냅샷을 반영한다 — 제시 버전이 현재보다 클 때만 갱신하고(순서 역전 방지·멱등),
     * 반영 여부를 반환한다. WITHDRAWN 반영은 {@code loginEmail}을 함께 파기한다(식별자 해제 + PII 파기).
     */
    public boolean applyUserStatus(LifecycleStatus status, long version) {
        if (version <= this.userStatusVersion) {
            return false;
        }
        this.userStatus = status;
        this.userStatusVersion = version;
        if (status == LifecycleStatus.WITHDRAWN) {
            this.loginEmail = null;
        }
        return true;
    }

    @Override
    public UUID getId() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public @Nullable Email getLoginEmail() {
        return loginEmail;
    }

    public LockState getLockState() {
        return lockState;
    }

    public @Nullable Instant getLockedAt() {
        return lockedAt;
    }

    public @Nullable LockReason getLockReason() {
        return lockReason;
    }

    public LifecycleStatus getUserStatus() {
        return userStatus;
    }

    public long getUserStatusVersion() {
        return userStatusVersion;
    }

    public List<String> getRoles() {
        return roles.isEmpty() ? List.of() : List.of(roles.split(ROLE_DELIMITER, -1));
    }
}
