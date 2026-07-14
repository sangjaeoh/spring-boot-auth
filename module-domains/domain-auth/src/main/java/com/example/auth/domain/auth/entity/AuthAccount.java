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

/**
 * 인증 계정 애그리거트 루트다. {@code userId}로 회원과 1:1 연결된다.
 *
 * <p>PK는 유저 서비스가 채번한 {@code UserId}를 그대로 쓴다({@code @GeneratedValue} 없음). 로그인 핫패스
 * 접근 판정은 유저 동기 호출 없이 로컬 스냅샷({@code userStatus}·{@code lockState})으로만 한다.
 * 잠금 전이·유저상태 동기화 컬럼/메서드는 writer(P4/P5)가 생길 때 추가한다.
 */
@Entity
@Table(schema = "auth", name = "auth_account")
public class AuthAccount extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Convert(converter = EmailConverter.class)
    @Column(name = "login_email", length = 320)
    private Email loginEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_state", length = 20)
    private LockState lockState;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", length = 20)
    private LifecycleStatus userStatus;

    protected AuthAccount() {}

    private AuthAccount(UUID userId, Email loginEmail) {
        this.userId = userId;
        this.loginEmail = loginEmail;
        this.lockState = LockState.NONE;
        this.userStatus = LifecycleStatus.ACTIVE;
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

    @Override
    public UUID getId() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Email getLoginEmail() {
        return loginEmail;
    }

    public LockState getLockState() {
        return lockState;
    }

    public LifecycleStatus getUserStatus() {
        return userStatus;
    }
}
