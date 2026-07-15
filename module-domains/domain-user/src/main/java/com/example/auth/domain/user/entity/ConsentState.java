package com.example.auth.domain.user.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 현재 동의 스냅샷 읽기모델이다({@code (userId, termsType)} 유니크).
 *
 * <p>{@code ConsentRecord} append 로그를 fold한 재구축 가능 캐시라 append-only 위배가 아니다. 갱신은
 * 로그 append와 같은 트랜잭션에서 수행돼 로그와 항상 정합하다.
 */
@Entity
@Table(schema = "usr", name = "consent_state")
public class ConsentState extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", length = 30)
    private TermsType termsType;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_action", length = 10)
    private ConsentAction currentAction;

    @Column(name = "agreed_version")
    @Nullable
    private Integer agreedVersion;

    protected ConsentState() {}

    private ConsentState(UUID id, UUID userId, TermsType termsType, ConsentAction action, @Nullable Integer version) {
        this.id = id;
        this.userId = userId;
        this.termsType = termsType;
        this.currentAction = action;
        this.agreedVersion = version;
    }

    /**
     * 최초 동의/철회 반영으로 스냅샷 행을 생성한다.
     */
    public static ConsentState create(UUID userId, TermsType termsType, ConsentAction action, int termsVersion) {
        return new ConsentState(
                UuidV7Generator.generate(),
                userId,
                termsType,
                action,
                action == ConsentAction.AGREE ? termsVersion : null);
    }

    /**
     * 새 동의/철회 append를 스냅샷에 반영한다(철회는 {@code agreedVersion}을 비운다).
     */
    public void apply(ConsentAction action, int termsVersion) {
        this.currentAction = action;
        this.agreedVersion = action == ConsentAction.AGREE ? termsVersion : null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public TermsType getTermsType() {
        return termsType;
    }

    public ConsentAction getCurrentAction() {
        return currentAction;
    }

    public @Nullable Integer getAgreedVersion() {
        return agreedVersion;
    }
}
