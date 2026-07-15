package com.example.auth.domain.generic.info;

import com.example.auth.domain.generic.entity.Notification;
import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.domain.generic.entity.NotificationStatus;
import java.util.UUID;

/**
 * 알림 발송 결과의 경계 조회 모델이다.
 */
public record NotificationInfo(
        UUID notificationId, NotificationChannel channel, NotificationStatus status, int retryCount) {

    public static NotificationInfo from(Notification notification) {
        return new NotificationInfo(
                notification.getId(),
                notification.getChannel(),
                notification.getStatus(),
                notification.getRetryCount());
    }
}
