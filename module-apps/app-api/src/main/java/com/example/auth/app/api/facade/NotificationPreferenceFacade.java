package com.example.auth.app.api.facade;

import com.example.auth.app.api.presentation.v1.NotificationPreferenceResponse;
import com.example.auth.domain.user.entity.NotificationCategory;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.service.NotificationPreferenceModifier;
import com.example.auth.domain.user.service.NotificationPreferenceReader;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 알림 수신 설정 조회·변경을 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지 않는다).
 */
@Component
public class NotificationPreferenceFacade {

    private final NotificationPreferenceReader notificationPreferenceReader;
    private final NotificationPreferenceModifier notificationPreferenceModifier;

    public NotificationPreferenceFacade(
            NotificationPreferenceReader notificationPreferenceReader,
            NotificationPreferenceModifier notificationPreferenceModifier) {
        this.notificationPreferenceReader = notificationPreferenceReader;
        this.notificationPreferenceModifier = notificationPreferenceModifier;
    }

    /**
     * 수신 설정 매트릭스(채널×카테고리)를 반환한다.
     */
    public NotificationPreferenceResponse get(UUID userId) {
        return NotificationPreferenceResponse.from(notificationPreferenceReader.get(userId));
    }

    /**
     * 해당 채널×카테고리 수신을 허용/거부한다(SECURITY 최소 연락 채널 유지 강제는 도메인이 거부).
     */
    public void update(UUID userId, NotificationChannel channel, NotificationCategory category, boolean allowed) {
        if (allowed) {
            notificationPreferenceModifier.allow(userId, channel, category);
        } else {
            notificationPreferenceModifier.disallow(userId, channel, category);
        }
    }
}
