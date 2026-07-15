package com.example.auth.domain.user.entity;

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
 * 약관 버전 애그리거트 루트다({@code (type, version)} 유니크, 발행 후 불변·append 전용).
 *
 * <p>동의는 항상 특정 (type, version)을 참조한다. 이 단계의 유일한 writer는 Flyway 시드다 — 버전 발행
 * ({@code publishVersion})·재동의 유발은 P4에서 추가한다(그때까지 읽기 전용이라 생성 팩토리를 두지 않는다).
 */
@Entity
@Table(schema = "usr", name = "terms_version")
public class TermsVersion extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30)
    private TermsType type;

    @Column(name = "version")
    private int version;

    @Column(name = "content")
    private String content;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

    protected TermsVersion() {}

    @Override
    public UUID getId() {
        return id;
    }

    public TermsType getType() {
        return type;
    }

    public int getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }
}
