package com.example.auth.domain.user.entity;

import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 역할-권한 매핑이다({@code @ManyToMany} 금지 — 연결 테이블을 독립 엔티티로 승격). 서로 다른 애그리거트
 * ({@link Role}·{@link Permission})를 이으므로 양쪽을 ID 값으로만 보관한다.
 *
 * <p>마이그레이션이 시딩하는 마스터 데이터이며 런타임 생성 경로가 없다 — 생성 팩토리를 두지 않는다.
 */
@Entity
@Table(schema = "usr", name = "role_permission")
public class RolePermission extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "permission_id")
    private UUID permissionId;

    protected RolePermission() {}

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }
}
