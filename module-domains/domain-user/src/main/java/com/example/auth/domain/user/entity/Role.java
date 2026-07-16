package com.example.auth.domain.user.entity;

import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 역할 애그리거트 루트다(RBAC 마스터). {@code name}은 유니크다.
 *
 * <p>역할 3종은 마이그레이션이 시딩하는 마스터 데이터이며 런타임 생성 경로가 없다 — 생성 팩토리를 두지
 * 않는다. 권한 매핑은 {@code role_permission}({@link RolePermission})이 잇는다.
 */
@Entity
@Table(schema = "usr", name = "role")
public class Role extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", length = 30)
    private RoleName name;

    protected Role() {}

    @Override
    public UUID getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }
}
