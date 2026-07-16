package com.example.auth.domain.generic.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 감사 로그 애그리거트 루트다(append-only WORM — DOMAIN_MODEL §3.2).
 *
 * <p>수정 경로가 없다: 엔티티는 전이 메서드를 두지 않고, 리포지토리 표면은 update/delete를 노출하지
 * 않으며, DB 트리거가 UPDATE/DELETE를 차단한다. 관리자 행위·권한 변경은 전/후 값
 * ({@code beforeValue}/{@code afterValue})을 함께 기록한다. 보존창(2년) 경과 후 PII crypto-shred는
 * 후속 배치 잡이 소유한다.
 */
@Entity
@Table(schema = "generic", name = "audit_log")
public class AuditLog extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "actor", length = 100)
    private String actor;

    @Column(name = "action", length = 100)
    private String action;

    @Column(name = "target", length = 100)
    @Nullable
    private String target;

    @Column(name = "before_value")
    @Nullable
    private String beforeValue;

    @Column(name = "after_value")
    @Nullable
    private String afterValue;

    @Column(name = "context")
    @Nullable
    private String context;

    @Column(name = "occurred_at")
    private Instant at;

    protected AuditLog() {}

    private AuditLog(
            UUID id,
            String actor,
            String action,
            @Nullable String target,
            @Nullable String beforeValue,
            @Nullable String afterValue,
            @Nullable String context,
            Instant at) {
        this.id = id;
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.context = context;
        this.at = at;
    }

    /**
     * 새 감사 기록을 생성한다.
     */
    public static AuditLog create(
            String actor,
            String action,
            @Nullable String target,
            @Nullable String beforeValue,
            @Nullable String afterValue,
            @Nullable String context,
            Instant at) {
        return new AuditLog(UuidV7Generator.generate(), actor, action, target, beforeValue, afterValue, context, at);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public @Nullable String getTarget() {
        return target;
    }

    public @Nullable String getBeforeValue() {
        return beforeValue;
    }

    public @Nullable String getAfterValue() {
        return afterValue;
    }

    public @Nullable String getContext() {
        return context;
    }

    public Instant getAt() {
        return at;
    }
}
