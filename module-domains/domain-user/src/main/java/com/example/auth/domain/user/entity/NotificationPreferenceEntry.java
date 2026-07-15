package com.example.auth.domain.user.entity;

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
import java.util.UUID;

/**
 * 채널×카테고리 수신 허용 엔트리다({@link NotificationPreference} 애그리거트의 자식,
 * {@code (preference, channel, category)} 유니크).
 */
@Entity
@Table(schema = "usr", name = "notification_preference_entry")
public class NotificationPreferenceEntry extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    // FK 소유(자식→부모). mappedBy 대상이자 프레임워크가 리플렉션으로만 읽는 필드라 @Keep로 마킹한다
    // (docs/entity-persistence.md — 미사용 필드 정적분석 마커). 물리 FK는 만들지 않는다(NO_CONSTRAINT).
    @Keep
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preference_user_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private NotificationPreference preference;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 10)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private NotificationCategory category;

    @Column(name = "allowed")
    private boolean allowed;

    protected NotificationPreferenceEntry() {}

    private NotificationPreferenceEntry(
            UUID id,
            NotificationPreference preference,
            NotificationChannel channel,
            NotificationCategory category,
            boolean allowed) {
        this.id = id;
        this.preference = preference;
        this.channel = channel;
        this.category = category;
        this.allowed = allowed;
    }

    static NotificationPreferenceEntry create(
            NotificationPreference preference,
            NotificationChannel channel,
            NotificationCategory category,
            boolean allowed) {
        return new NotificationPreferenceEntry(UuidV7Generator.generate(), preference, channel, category, allowed);
    }

    void changeAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public boolean isAllowed() {
        return allowed;
    }
}
