package com.example.auth.domain.generic.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.generic.entity.Notification;
import com.example.auth.domain.generic.entity.NotificationCategory;
import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.domain.generic.entity.NotificationStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * generic 스키마 영속 E2E: Flyway 마이그레이션 → {@code ddl-auto=validate} → 저장/조회·유니크 강제.
 */
@SpringBootTest
@Import(JpaConfig.class)
@Testcontainers
class GenericPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void savesAndFindsBySourceEventIdAndChannel() {
        UUID sourceEventId = UUID.randomUUID();
        Notification saved = notificationRepository.save(Notification.create(
                UUID.randomUUID(),
                NotificationCategory.SECURITY,
                NotificationChannel.EMAIL,
                "security.new-device",
                "{\"deviceName\":\"iPhone\"}",
                sourceEventId));

        Optional<Notification> found =
                notificationRepository.findBySourceEventIdAndChannel(sourceEventId, NotificationChannel.EMAIL);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void enforcesSourceEventChannelUniqueness() {
        UUID sourceEventId = UUID.randomUUID();
        notificationRepository.save(notification(sourceEventId, NotificationChannel.EMAIL));

        assertThatThrownBy(() ->
                        notificationRepository.saveAndFlush(notification(sourceEventId, NotificationChannel.EMAIL)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameEventOnDifferentChannels() {
        UUID sourceEventId = UUID.randomUUID();
        notificationRepository.save(notification(sourceEventId, NotificationChannel.EMAIL));
        notificationRepository.saveAndFlush(notification(sourceEventId, NotificationChannel.SMS));

        assertThat(notificationRepository.findBySourceEventIdAndChannel(sourceEventId, NotificationChannel.SMS))
                .isPresent();
    }

    private Notification notification(UUID sourceEventId, NotificationChannel channel) {
        return Notification.create(
                UUID.randomUUID(), NotificationCategory.SECURITY, channel, "security.test", "{}", sourceEventId);
    }
}
