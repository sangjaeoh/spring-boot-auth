package com.example.auth.domain.user.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.user.entity.NotificationCategory;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.NotificationPreference;
import com.example.auth.domain.user.event.NotificationPreferenceChanged;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.NotificationPreferenceRepository;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 수신 설정의 변경을 담당한다 — 회원별 채널×카테고리 허용/거부와 마케팅 동의 동기화.
 */
@Service
public class NotificationPreferenceModifier {

    private final NotificationPreferenceRepository repository;
    private final MessagePublisher messagePublisher;

    public NotificationPreferenceModifier(
            NotificationPreferenceRepository repository, MessagePublisher messagePublisher) {
        this.repository = repository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 해당 채널×카테고리 수신을 허용한다.
     *
     * @throws UserException 설정이 존재하지 않으면(404)
     */
    @Transactional
    public void allow(UUID userId, NotificationChannel channel, NotificationCategory category) {
        NotificationPreference preference = getPreference(userId);
        preference.allow(channel, category);
        messagePublisher.publish(new NotificationPreferenceChanged(userId, channel, category, true, Instant.now()));
    }

    /**
     * 해당 채널×카테고리 수신을 거부한다.
     *
     * @throws UserException 설정이 존재하지 않으면(404); SECURITY의 마지막 연락 채널 해제 시도면(400)
     */
    @Transactional
    public void disallow(UUID userId, NotificationChannel channel, NotificationCategory category) {
        NotificationPreference preference = getPreference(userId);
        preference.disallow(channel, category);
        messagePublisher.publish(new NotificationPreferenceChanged(userId, channel, category, false, Instant.now()));
    }

    /**
     * 마케팅 동의/철회를 수신 매트릭스에 반영한다(채널 미지정이면 전 채널). 설정 미존재는 무시한다 —
     * 탈퇴 등으로 설정이 사라진 뒤 도착한 이벤트 재전달을 멱등 흡수한다.
     */
    @Transactional
    public void syncMarketingFromConsent(UUID userId, boolean agreed, @Nullable NotificationChannel channel) {
        NotificationPreference preference = repository.findById(userId).orElse(null);
        if (preference == null) {
            return;
        }
        preference.syncMarketingFromConsent(agreed, channel);
    }

    private NotificationPreference getPreference(UUID userId) {
        return repository
                .findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.NOTIFICATION_PREFERENCE_NOT_FOUND));
    }
}
