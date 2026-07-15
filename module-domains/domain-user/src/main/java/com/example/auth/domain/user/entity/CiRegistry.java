package com.example.auth.domain.user.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * CI 원장 애그리거트 루트다 — {@code ciHash} 유일성으로 동일인 다중 활성가입을 봉쇄한다.
 *
 * <p>유일성은 DB 유니크 인덱스가 hard-enforce한다(TOCTOU 회피). 가입 중 조기 판정은 읽기 soft-check
 * ({@code CiUniquenessValidator})이고, {@code link}·{@code relink}는 {@code CreateUser} 단일 트랜잭션
 * 안에서만 수행된다. 탈퇴 시 tombstone(WITHDRAWN_RETAINED)으로 전이해 부정재가입 방지 목적의 유한
 * 보존창(기본 6개월) 동안만 해시를 남기고, 창 경과 시 배치가 행을 파기한다.
 */
@Entity
@Table(schema = "usr", name = "ci_registry")
public class CiRegistry extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "ci_hash", length = 128)
    private String ciHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private CiStatus status;

    @Column(name = "linked_user_id")
    @Nullable
    private UUID linkedUserId;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "withdrawn_at")
    @Nullable
    private Instant withdrawnAt;

    protected CiRegistry() {}

    private CiRegistry(UUID id, String ciHash, UUID linkedUserId, Instant firstSeenAt) {
        this.id = id;
        this.ciHash = ciHash;
        this.status = CiStatus.ACTIVE_LINKED;
        this.linkedUserId = linkedUserId;
        this.firstSeenAt = firstSeenAt;
    }

    /**
     * 회원에 연결된 원장 항목을 생성한다((신규)→ACTIVE_LINKED). 유일성은 저장 시 유니크 인덱스가 강제한다.
     */
    public static CiRegistry link(String ciHash, UUID userId, Instant firstSeenAt) {
        return new CiRegistry(UuidV7Generator.generate(), ciHash, userId, firstSeenAt);
    }

    /**
     * 탈퇴 tombstone으로 전이한다(ACTIVE_LINKED→WITHDRAWN_RETAINED, 연결 해제). {@code withdrawnAt}이
     * 보존창·재가입 쿨다운의 기산점이 된다.
     *
     * @throws UserException 활성 연결 상태가 아니면(409)
     */
    public void retainOnWithdrawal(Instant at) {
        if (status != CiStatus.ACTIVE_LINKED) {
            throw new UserException(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = CiStatus.WITHDRAWN_RETAINED;
        this.linkedUserId = null;
        this.withdrawnAt = at;
    }

    /**
     * 쿨다운이 지난 tombstone을 새 회원에 재연결한다(WITHDRAWN_RETAINED→ACTIVE_LINKED). 최초 등록
     * 시각은 유지한다.
     *
     * @throws UserException 이미 활성 연결이면 — 재가입 경합의 후발 트랜잭션(409)
     */
    public void relink(UUID userId) {
        if (status != CiStatus.WITHDRAWN_RETAINED) {
            throw new UserException(UserErrorCode.DUPLICATE_CI);
        }
        this.status = CiStatus.ACTIVE_LINKED;
        this.linkedUserId = userId;
        this.withdrawnAt = null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getCiHash() {
        return ciHash;
    }

    public CiStatus getStatus() {
        return status;
    }

    public @Nullable UUID getLinkedUserId() {
        return linkedUserId;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public @Nullable Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
