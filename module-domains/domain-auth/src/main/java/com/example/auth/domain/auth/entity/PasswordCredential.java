package com.example.auth.domain.auth.entity;

import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 로컬 로그인 비밀번호 자격증명 애그리거트 루트다(계정당 0..1).
 *
 * <p>안전 해시로만 저장한다(원문·가역 암호 불가). 최근 N 재사용 금지 이력({@link PasswordHistoryEntry})을
 * 자식으로 소유하고 변경 시 축출까지 이 루트가 원자적으로 수행한다. 현재 해시는 로그인 핫패스 대조용으로
 * 이력과 별개로 비정규화 보관한다(이력 컬렉션은 변경·재설정 저빈도 경로에서만 로딩).
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

    @OneToMany(mappedBy = "credential", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PasswordHistoryEntry> history;

    protected PasswordCredential() {}

    private PasswordCredential(UUID userId, String passwordHash, HashAlgorithm algorithm, Instant changedAt) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.algorithm = algorithm;
        this.changedAt = changedAt;
        this.history = new HashSet<>();
        this.history.add(PasswordHistoryEntry.create(this, passwordHash, algorithm, changedAt));
    }

    /**
     * 인코딩된 해시로 자격증명을 생성하고 최초 이력을 적재한다.
     */
    public static PasswordCredential create(
            UUID userId, String passwordHash, HashAlgorithm algorithm, Instant changedAt) {
        return new PasswordCredential(userId, passwordHash, algorithm, changedAt);
    }

    /**
     * 현재 해시를 새 해시로 교체하고 이력에 적재한 뒤 최근 {@code historyLimit}개만 남긴다.
     */
    public void changePassword(String newPasswordHash, HashAlgorithm algorithm, Instant changedAt, int historyLimit) {
        this.passwordHash = newPasswordHash;
        this.algorithm = algorithm;
        this.changedAt = changedAt;
        this.history.add(PasswordHistoryEntry.create(this, newPasswordHash, algorithm, changedAt));
        evictOldestBeyond(historyLimit);
    }

    /**
     * 재사용 금지 대조용 최근 이력 해시 목록을 반환한다(현재 해시 포함).
     */
    public List<String> historyHashes() {
        return history.stream().map(PasswordHistoryEntry::getPasswordHash).toList();
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

    private void evictOldestBeyond(int historyLimit) {
        while (history.size() > historyLimit) {
            PasswordHistoryEntry oldest = history.stream()
                    .min(Comparator.comparing(PasswordHistoryEntry::getChangedAt)
                            .thenComparing(PasswordHistoryEntry::getId))
                    .orElseThrow();
            history.remove(oldest);
        }
    }
}
