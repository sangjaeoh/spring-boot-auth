package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.NotificationPreference;
import com.example.auth.domain.user.repository.NotificationPreferenceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 수신 설정 생성을 담당한다(가입 시 전체 채널×카테고리 매트릭스 초기화).
 */
@Service
public class NotificationPreferenceAppender {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceAppender(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    /**
     * 회원의 수신 설정을 생성한다 — 비마케팅은 허용, 마케팅은 가입 시 동의값을 따른다.
     */
    @Transactional
    public void initialize(UUID userId, boolean marketingAgreed) {
        repository.save(NotificationPreference.create(userId, marketingAgreed));
    }
}
