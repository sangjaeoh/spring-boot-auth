package com.example.auth.domain.auth.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import com.google.errorprone.annotations.Keep;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 과거 비밀번호 해시 이력 엔트리다({@link PasswordCredential} 애그리거트의 자식).
 *
 * <p>최근 N 재사용 금지 검증에만 쓰이며, 부모 자격증명이 변경될 때만 생성되고 그 불변식만을 위해 읽힌다 —
 * 독립 생명주기가 없어 자격증명 애그리거트에 속한다(cascade·orphanRemoval로 부모가 생성·축출을 소유).
 */
@Entity
@Table(schema = "auth", name = "password_history")
public class PasswordHistoryEntry extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    // FK 소유(자식→부모). mappedBy 대상이자 프레임워크가 리플렉션으로만 읽는 필드라 @Keep로 마킹한다
    // (docs/entity-persistence.md — 미사용 필드 정적분석 마커). 물리 FK는 만들지 않는다(NO_CONSTRAINT).
    @Keep
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private PasswordCredential credential;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", length = 20)
    private HashAlgorithm algorithm;

    @Column(name = "changed_at")
    private Instant changedAt;

    protected PasswordHistoryEntry() {}

    private PasswordHistoryEntry(
            UUID id, PasswordCredential credential, String passwordHash, HashAlgorithm algorithm, Instant changedAt) {
        this.id = id;
        this.credential = credential;
        this.passwordHash = passwordHash;
        this.algorithm = algorithm;
        this.changedAt = changedAt;
    }

    /**
     * 주어진 자격증명 시점의 해시 이력 엔트리를 생성한다.
     */
    static PasswordHistoryEntry create(
            PasswordCredential credential, String passwordHash, HashAlgorithm algorithm, Instant changedAt) {
        return new PasswordHistoryEntry(UuidV7Generator.generate(), credential, passwordHash, algorithm, changedAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public HashAlgorithm getAlgorithm() {
        return algorithm;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
