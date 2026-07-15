package com.example.auth.domain.generic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.domain.generic.entity.Notification;
import com.example.auth.domain.generic.entity.NotificationCategory;
import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.domain.generic.entity.NotificationStatus;
import com.example.auth.domain.generic.info.NotificationInfo;
import com.example.auth.domain.generic.port.NotificationSender;
import com.example.auth.domain.generic.repository.NotificationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    private NotificationRepository repository;
    private NotificationSender sender;
    private NotificationProcessor processor;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        sender = mock(NotificationSender.class);
        processor = new NotificationProcessor(repository, sender);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsRecordsAndSendsNewNotification() {
        UUID sourceEventId = UUID.randomUUID();
        when(repository.findBySourceEventIdAndChannel(sourceEventId, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());

        NotificationInfo info = dispatch(sourceEventId);

        assertThat(info.status()).isEqualTo(NotificationStatus.SENT);
        verify(sender).send(NotificationChannel.EMAIL, "user@example.com", "security.new-device", "{}");
    }

    @Test
    void skipsAlreadySentNotificationWithoutResending() {
        UUID sourceEventId = UUID.randomUUID();
        Notification sent = notification(sourceEventId);
        sent.markSent(NOW);
        when(repository.findBySourceEventIdAndChannel(sourceEventId, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(sent));

        NotificationInfo info = dispatch(sourceEventId);

        assertThat(info.status()).isEqualTo(NotificationStatus.SENT);
        verify(sender, never()).send(any(), any(), any(), any());
    }

    @Test
    void retriesFailedNotificationAndIncrementsCount() {
        UUID sourceEventId = UUID.randomUUID();
        Notification failed = notification(sourceEventId);
        failed.markFailed("smtp down");
        when(repository.findBySourceEventIdAndChannel(sourceEventId, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(failed));

        NotificationInfo info = dispatch(sourceEventId);

        assertThat(info.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(info.retryCount()).isEqualTo(1);
        verify(sender).send(NotificationChannel.EMAIL, "user@example.com", "security.new-device", "{}");
    }

    @Test
    void absorbsSendFailureAsFailedHistory() {
        UUID sourceEventId = UUID.randomUUID();
        when(repository.findBySourceEventIdAndChannel(sourceEventId, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        Mockito.doThrow(new IllegalStateException("smtp down")).when(sender).send(any(), any(), any(), any());

        NotificationInfo info = dispatch(sourceEventId);

        assertThat(info.status()).isEqualTo(NotificationStatus.FAILED);
    }

    private NotificationInfo dispatch(UUID sourceEventId) {
        return processor.dispatch(
                UUID.randomUUID(),
                NotificationCategory.SECURITY,
                NotificationChannel.EMAIL,
                "security.new-device",
                "{}",
                "user@example.com",
                sourceEventId,
                NOW);
    }

    private Notification notification(UUID sourceEventId) {
        return Notification.create(
                UUID.randomUUID(),
                NotificationCategory.SECURITY,
                NotificationChannel.EMAIL,
                "security.new-device",
                "{}",
                sourceEventId);
    }
}
