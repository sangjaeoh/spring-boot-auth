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
 * 약관 문서(유형) 애그리거트 루트다. 유형 유일성과 필수 동의 여부(가입 전제 판정)를 소유한다.
 *
 * <p>이 단계의 유일한 writer는 Flyway 시드다 — 유형 등록({@code registerType}) 등 Java 생성 경로는
 * 약관 관리가 등장하는 P4에서 추가한다(그때까지 읽기 전용이라 생성 팩토리를 두지 않는다).
 */
@Entity
@Table(schema = "usr", name = "terms_document")
public class TermsDocument extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 30)
    private TermsType type;

    @Column(name = "required")
    private boolean required;

    protected TermsDocument() {}

    @Override
    public UUID getId() {
        return id;
    }

    public TermsType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }
}
