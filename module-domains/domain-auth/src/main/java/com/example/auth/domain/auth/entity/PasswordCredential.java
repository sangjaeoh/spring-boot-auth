package com.example.auth.domain.auth.entity;

import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 로컬 로그인 비밀번호 자격증명 애그리거트 루트다(계정당 0..1).
 *
 * <p>안전 해시로만 저장한다(원문·가역 암호 불가). 최근 N 재사용 금지 이력은 후속 슬라이스에서 도입한다.
 */
@Entity
@Table(schema = "auth", name = "password_credential")
public class PasswordCredential extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", length = 20)
    private HashAlgorithm algorithm;

    @Column(name = "changed_at")
    private Instant changedAt;

    protected PasswordCredential() {}

    private PasswordCredential(UUID userId, String passwordHash, HashAlgorithm algorithm, Instant changedAt) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.algorithm = algorithm;
        this.changedAt = changedAt;
    }

    /**
     * 인코딩된 해시로 자격증명을 생성한다.
     */
    public static PasswordCredential create(
            UUID userId, String passwordHash, HashAlgorithm algorithm, Instant changedAt) {
        return new PasswordCredential(userId, passwordHash, algorithm, changedAt);
    }

    @Override
    public UUID getId() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
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
