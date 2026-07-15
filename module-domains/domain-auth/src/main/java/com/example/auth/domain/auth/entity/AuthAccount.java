package com.example.auth.domain.auth.entity;

import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
 * 잠금 전이 메서드는 writer(P5)가 생길 때 추가한다.
 */
@Entity
@Table(schema = "auth", name = "auth_account")
public class AuthAccount extends BaseTimeEntity<UUID> {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", length = 20)
    private LifecycleStatus userStatus;

    @Column(name = "user_status_version")
    private long userStatusVersion;

    protected AuthAccount() {}

    private AuthAccount(UUID userId, Email loginEmail) {
        this.userId = userId;
        this.loginEmail = loginEmail;
        this.lockState = LockState.NONE;
        this.userStatus = LifecycleStatus.ACTIVE;
        this.userStatusVersion = 0;
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
     * 로그인 식별자를 변경한다(연락용 {@code contactEmail}과 독립). 유일성은 유니크 인덱스가 backstop한다.
     */
    public void changeLoginEmail(Email newEmail) {
        this.loginEmail = newEmail;
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

    public LifecycleStatus getUserStatus() {
        return userStatus;
    }

    public long getUserStatusVersion() {
        return userStatusVersion;
    }
}
