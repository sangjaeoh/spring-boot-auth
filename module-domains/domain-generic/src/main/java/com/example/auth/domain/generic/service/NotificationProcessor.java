package com.example.auth.domain.generic.service;

import com.example.auth.domain.generic.entity.Notification;
import com.example.auth.domain.generic.entity.NotificationCategory;
import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.domain.generic.entity.NotificationStatus;
import com.example.auth.domain.generic.info.NotificationInfo;
import com.example.auth.domain.generic.port.NotificationSender;
import com.example.auth.domain.generic.repository.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 발송 이력 기록과 포트 발송을 소유한다(PENDING 생성 → 발송 → SENT/FAILED).
 *
 * <p>{@code (sourceEventId, channel)} 멱등: 이미 SENT면 스킵하고, FAILED면 {@code retry()}로 재발송한다 —
 * 이벤트 재전달(DLQ 재시도 포함)이 이 경로로 수렴해 발송 실패 재시도가 메시징 인프라를 재사용한다.
 */
@Service
public class NotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final NotificationSender notificationSender;

    public NotificationProcessor(NotificationRepository notificationRepository, NotificationSender notificationSender) {
        this.notificationRepository = notificationRepository;
        this.notificationSender = notificationSender;
    }

    /**
     * 알림을 기록하고 발송한다. 발송 실패는 예외가 아니라 FAILED 이력으로 흡수한다 — 호출자는 반환
     * 상태로 재시도 필요를 판단한다.
     *
     * <p>{@code REQUIRES_NEW}: 소비 트랜잭션(멱등 원장)과 분리 커밋한다. 소비자가 실패를 DLQ로 넘기며
     * 롤백해도 발송 이력(SENT·FAILED)은 남아, 재전달 시 SENT 채널의 이중 발송을 막고 FAILED 채널의
     * {@code retryCount}가 이어진다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationInfo dispatch(
            UUID userId,
            NotificationCategory category,
            NotificationChannel channel,
            String templateId,
            String payload,
            String target,
            UUID sourceEventId,
            Instant now) {
        Notification notification = notificationRepository
                .findBySourceEventIdAndChannel(sourceEventId, channel)
                .orElse(null);
        if (notification == null) {
            notification = notificationRepository.save(
                    Notification.create(userId, category, channel, templateId, payload, sourceEventId));
        } else if (notification.getStatus() == NotificationStatus.SENT) {
            return NotificationInfo.from(notification);
        } else if (notification.getStatus() == NotificationStatus.FAILED) {
            notification.retry();
        }
        try {
            notificationSender.send(channel, target, templateId, payload);
            notification.markSent(now);
        } catch (RuntimeException e) {
            notification.markFailed(
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return NotificationInfo.from(notification);
    }
}
