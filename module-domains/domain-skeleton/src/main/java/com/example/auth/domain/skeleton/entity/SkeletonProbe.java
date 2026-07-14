package com.example.auth.domain.skeleton.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Phase 0 walking skeleton 프로브 엔티티.
 *
 * <p>마이그레이션→엔티티→리포지토리 파이프라인과 {@code ddl-auto=validate} 정합을 증명하기 위한 최소
 * 엔티티다. 도메인 의미는 없으며 Phase 1a에서 실제 애그리거트로 대체한다.
 */
@Entity
@Table(schema = "skeleton", name = "skeleton_probe")
public class SkeletonProbe extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(length = 200)
    private String label;

    protected SkeletonProbe() {}

    private SkeletonProbe(UUID id, String label) {
        this.id = id;
        this.label = label;
    }

    /**
     * 새 프로브를 생성한다.
     */
    public static SkeletonProbe create(String label) {
        return new SkeletonProbe(UuidV7Generator.generate(), label);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }
}
