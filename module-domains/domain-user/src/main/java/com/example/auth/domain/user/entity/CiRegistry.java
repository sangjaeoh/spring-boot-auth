package com.example.auth.domain.user.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
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
 * ({@code CiUniquenessValidator})이고, {@code link}는 {@code CreateUser} 단일 트랜잭션 안에서만 수행된다
 * (다음 슬라이스). 탈퇴 tombstone 전이({@code retainOnWithdrawal})는 P4에서 추가한다.
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
