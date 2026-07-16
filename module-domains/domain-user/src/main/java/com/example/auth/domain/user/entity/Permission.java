package com.example.auth.domain.user.entity;

import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 권한 애그리거트 루트다(RBAC 마스터). {@code (resource, action)}은 유니크다.
 *
 * <p>마이그레이션이 시딩하는 마스터 데이터이며 런타임 생성 경로가 없다 — 생성 팩토리를 두지 않는다.
 * 집행은 토큰 roles 클레임 기반이고 이 테이블은 역할별 허용 오퍼레이션의 정본 매핑을 든다.
 */
@Entity
@Table(schema = "usr", name = "permission")
public class Permission extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "resource", length = 50)
    private String resource;

    @Column(name = "action", length = 50)
    private String action;

    protected Permission() {}

    @Override
    public UUID getId() {
        return id;
    }

    public String getResource() {
        return resource;
    }

    public String getAction() {
        return action;
    }
}
