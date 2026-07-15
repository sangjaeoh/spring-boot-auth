package com.example.auth.domain.generic.entity;

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
import org.jspecify.annotations.Nullable;

/**
 * 알림 발송 이력·상태 애그리거트 루트다(PENDING → SENT/FAILED).
 *
 * <p>{@code (sourceEventId, channel)} 유니크가 멱등 발송의 자연키다 — 통합 이벤트는 at-least-once로
 * 재전달되므로 같은 이벤트·채널의 재소비는 새 행이 아니라 기존 행의 재시도({@link #retry()})로 수렴한다.
 */
@Entity
@Table(schema = "generic", name = "notification")
public class Notification extends BaseTimeEntity<UUID> {

    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 10)
    private NotificationChannel channel;

    @Column(name = "template_id", length = 100)
    private String templateId;

    @Column(name = "payload")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10)
    private NotificationStatus status;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "failure_reason", length = FAILURE_REASON_MAX_LENGTH)
    @Nullable
    private String failureReason;

    @Column(name = "sent_at")
    @Nullable
    private Instant sentAt;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    protected Notification() {}

    private Notification(
            UUID id,
            UUID userId,
            NotificationCategory category,
            NotificationChannel channel,
            String templateId,
            String payload,
            UUID sourceEventId) {
        this.id = id;
        this.userId = userId;
        this.category = category;
        this.channel = channel;
        this.templateId = templateId;
        this.payload = payload;
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
        this.sourceEventId = sourceEventId;
    }

    /**
     * 발송 대기(PENDING) 알림을 생성한다.
     */
    public static Notification create(
            UUID userId,
            NotificationCategory category,
            NotificationChannel channel,
            String templateId,
            String payload,
            UUID sourceEventId) {
        return new Notification(
                UuidV7Generator.generate(), userId, category, channel, templateId, payload, sourceEventId);
    }

    /**
     * 발송 완료로 전이한다(PENDING → SENT).
     */
    public void markSent(Instant at) {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException("PENDING 알림만 SENT로 전이할 수 있다: " + status);
        }
        this.status = NotificationStatus.SENT;
        this.sentAt = at;
        this.failureReason = null;
    }

    /**
     * 발송 실패로 전이한다(PENDING → FAILED).
     */
    public void markFailed(String reason) {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException("PENDING 알림만 FAILED로 전이할 수 있다: " + status);
        }
        this.status = NotificationStatus.FAILED;
        this.failureReason =
                reason.length() > FAILURE_REASON_MAX_LENGTH ? reason.substring(0, FAILURE_REASON_MAX_LENGTH) : reason;
    }

    /**
     * 실패 알림을 재발송 대기로 되돌린다(FAILED → PENDING, {@code retryCount} 증가).
     */
    public void retry() {
        if (status != NotificationStatus.FAILED) {
            throw new IllegalStateException("FAILED 알림만 재시도할 수 있다: " + status);
        }
        this.status = NotificationStatus.PENDING;
        this.retryCount++;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getPayload() {
        return payload;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public @Nullable String getFailureReason() {
        return failureReason;
    }

    public @Nullable Instant getSentAt() {
        return sentAt;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }
}
