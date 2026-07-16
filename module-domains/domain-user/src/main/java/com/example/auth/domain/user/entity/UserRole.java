package com.example.auth.domain.user.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 사용자-역할 배정이다(DOMAIN_MODEL §1.7). {@code (userId, roleId)} 유니크는 유니크 인덱스가 backstop한다.
 *
 * <p>회원·역할은 서로 다른 애그리거트이므로 양쪽을 ID 값으로만 보관한다. 배정 해제는 행 삭제다
 * (상태 없는 순수 배정 행 — 변경 이력은 감사 로그가 소유).
 */
@Entity
@Table(schema = "usr", name = "user_role")
public class UserRole extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    protected UserRole() {}

    private UserRole(UUID id, UUID userId, UUID roleId, Instant assignedAt) {
        this.id = id;
        this.userId = userId;
        this.roleId = roleId;
        this.assignedAt = assignedAt;
    }

    /**
     * 역할 배정을 생성한다.
     */
    public static UserRole create(UUID userId, UUID roleId, Instant assignedAt) {
        return new UserRole(UuidV7Generator.generate(), userId, roleId, assignedAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
