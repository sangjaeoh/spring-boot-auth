package com.example.auth.domain.generic.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

    @Test
    void createsPendingNotification() {
        Notification notification = notification();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRetryCount()).isZero();
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getFailureReason()).isNull();
    }

    @Test
    void marksSentFromPending() {
        Notification notification = notification();
        Instant at = Instant.parse("2026-07-16T00:00:00Z");

        notification.markSent(at);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(at);
    }

    @Test
    void rejectsMarkSentWhenNotPending() {
        Notification notification = notification();
        notification.markSent(Instant.now());

        assertThatThrownBy(() -> notification.markSent(Instant.now())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void marksFailedWithTruncatedReason() {
        Notification notification = notification();

        notification.markFailed("x".repeat(600));

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getFailureReason()).hasSize(500);
    }

    @Test
    void retryReturnsFailedToPendingAndCounts() {
        Notification notification = notification();
        notification.markFailed("smtp down");

        notification.retry();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getRetryCount()).isEqualTo(1);
    }

    @Test
    void rejectsRetryWhenNotFailed() {
        Notification notification = notification();

        assertThatThrownBy(notification::retry).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retriedNotificationCanBeSentAndClearsFailureReason() {
        Notification notification = notification();
        notification.markFailed("smtp down");
        notification.retry();

        notification.markSent(Instant.now());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getFailureReason()).isNull();
        assertThat(notification.getRetryCount()).isEqualTo(1);
    }

    private Notification notification() {
        return Notification.create(
                UUID.randomUUID(),
                NotificationCategory.SECURITY,
                NotificationChannel.EMAIL,
                "security.new-device",
                "{}",
                UUID.randomUUID());
    }
}
