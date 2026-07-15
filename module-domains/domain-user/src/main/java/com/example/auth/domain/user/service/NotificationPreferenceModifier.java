package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.NotificationPreference;
import com.example.auth.domain.user.repository.NotificationPreferenceRepository;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 수신 설정의 마케팅 매트릭스를 동의 이력과 동기화한다({@code ConsentGiven/Withdrawn(MARKETING)}
 * 소비 경로). 회원별 허용/거부 API는 수신설정 슬라이스가 추가한다.
 */
@Service
public class NotificationPreferenceModifier {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceModifier(NotificationPreferenceRepository repository) {
        this.repository = repository;
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
}
