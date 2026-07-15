package com.example.auth.domain.user.entity;

import com.example.auth.common.core.id.UuidV7Generator;
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
 * <p>동의는 항상 특정 (type, version)을 참조한다. 발행 후 불변 — 수정 메서드가 없고 새 내용은 새 버전
 * 발행으로만 표현한다. 버전 단조 증가는 발행 서비스가 직전+1로 계산하고 유니크 인덱스가 backstop한다.
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

    private TermsVersion(UUID id, TermsType type, int version, String content, Instant effectiveFrom) {
        this.id = id;
        this.type = type;
        this.version = version;
        this.content = content;
        this.effectiveFrom = effectiveFrom;
    }

    /**
     * 새 약관 버전을 생성한다(발행 후 불변).
     */
    public static TermsVersion create(TermsType type, int version, String content, Instant effectiveFrom) {
        return new TermsVersion(UuidV7Generator.generate(), type, version, content, effectiveFrom);
    }

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
