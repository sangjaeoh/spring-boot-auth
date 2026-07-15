package com.example.auth.domain.generic.repository;

import com.example.auth.domain.generic.entity.Notification;
import com.example.auth.domain.generic.entity.NotificationChannel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findBySourceEventIdAndChannel(UUID sourceEventId, NotificationChannel channel);
}
