package com.example.auth.domain.user.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 본인인증 애그리거트 루트다. 외부 본인확인기관 실명확인의 요청·결과를 소유한다.
 *
 * <p>PK가 온보딩이 참조하는 {@code VerificationId}다. 결과 PII는 암호화 컬럼으로만 저장하고 CI는 원문
 * 없이 salted HMAC({@code ciHash})만 남는다. {@code userId}는 정회원 생성({@code CreateUser}) 시 연결되며
 * 진행 중엔 null이다. 완료({@code complete}) 후 결과는 불변이다.
 */
@Entity
@Table(schema = "usr", name = "identity_verification")
public class IdentityVerification extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    @Nullable
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 10)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private VerificationStatus status;

    @Embedded
    @Nullable
    private VerificationResult result;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "verified_at")
    @Nullable
    private Instant verifiedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected IdentityVerification() {}

    private IdentityVerification(UUID id, Provider provider, Instant requestedAt, Instant expiresAt) {
        this.id = id;
        this.provider = provider;
        this.status = VerificationStatus.REQUESTED;
        this.requestedAt = requestedAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 실명확인 요청을 생성한다(최초 REQUESTED, 진행 만료 시각 세팅).
     */
    public static IdentityVerification create(Provider provider, Instant requestedAt, Instant expiresAt) {
        return new IdentityVerification(UuidV7Generator.generate(), provider, requestedAt, expiresAt);
    }

    /**
     * 실명확인 성공 결과를 확정한다(REQUESTED→VERIFIED). 이후 결과는 불변이다.
     *
     * @throws UserException REQUESTED가 아닌 상태에서 호출 시(409)
     */
    public void complete(VerificationResult result, Instant verifiedAt) {
        if (status != VerificationStatus.REQUESTED) {
            throw new UserException(UserErrorCode.VERIFICATION_STATE_INVALID);
        }
        this.status = VerificationStatus.VERIFIED;
        this.result = result;
        this.verifiedAt = verifiedAt;
    }

    /**
     * 정회원 생성({@code CreateUser}) 시 회원을 연결한다(VERIFIED·미연결에서 1회만). 연결된
     * {@code userId}는 이 인증 건이 가입 커밋에 소비되었다는 멱등 영수증이다.
     *
     * @throws UserException VERIFIED가 아니거나 이미 연결된 상태에서 호출 시(409)
     */
    public void attachUser(UUID userId) {
        if (status != VerificationStatus.VERIFIED || this.userId != null) {
            throw new UserException(UserErrorCode.VERIFICATION_STATE_INVALID);
        }
        this.userId = userId;
    }

    /**
     * 실명확인 실패로 종결한다(REQUESTED→FAILED).
     *
     * @throws UserException REQUESTED가 아닌 상태에서 호출 시(409)
     */
    public void fail() {
        if (status != VerificationStatus.REQUESTED) {
            throw new UserException(UserErrorCode.VERIFICATION_STATE_INVALID);
        }
        this.status = VerificationStatus.FAILED;
    }

    /**
     * 진행 만료로 종결한다(REQUESTED→EXPIRED). 기관 호출이 런타임 예외로 남긴 REQUESTED 잔존을 수렴하는
     * 스윕 전용 전이다 — 기관에선 완료됐을 수 있으므로 FAILED로 오분류하지 않는다.
     *
     * @throws UserException REQUESTED가 아닌 상태에서 호출 시(409)
     */
    public void expire() {
        if (status != VerificationStatus.REQUESTED) {
            throw new UserException(UserErrorCode.VERIFICATION_STATE_INVALID);
        }
        this.status = VerificationStatus.EXPIRED;
    }

    /**
     * 결과 PII를 파기한다(암호문 컬럼 null 소거 — crypto-shred 등가). 요청·상태 사실은 유지하고 결과
     * 값만 소거한다(회원 탈퇴·보존창 경과 파기가 호출).
     */
    public void purgeResult() {
        this.result = null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public @Nullable UUID getUserId() {
        return userId;
    }

    public Provider getProvider() {
        return provider;
    }

    public VerificationStatus getStatus() {
        return status;
    }

    public @Nullable VerificationResult getResult() {
        return result;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public @Nullable Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
