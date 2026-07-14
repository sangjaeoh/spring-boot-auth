package com.example.auth.common.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 JPA 엔티티의 시각·식별 기반 슈퍼타입이다.
 *
 * <p>{@code createdAt}·{@code updatedAt}은 JPA Auditing이 채운다. {@link Persistable}을 구현해
 * {@code createdAt == null}이면 신규로 판정하고 {@code persist()}로 직행해 merge penalty를 방어한다.
 * 동등성은 {@code create()}에서 확정되는 수동 UUIDv7 식별자로 판정한다(docs/entity-persistence.md).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity<ID extends Serializable> implements Persistable<ID> {

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean isNew() {
        return this.createdAt == null;
    }

    @Override
    public abstract ID getId();

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseTimeEntity<?> that)) {
            return false;
        }
        return getId() != null && getId().equals(that.getId());
    }

    @Override
    public final int hashCode() {
        return getId() == null ? 0 : getId().hashCode();
    }
}
