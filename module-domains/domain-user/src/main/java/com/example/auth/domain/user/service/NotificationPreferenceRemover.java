package com.example.auth.domain.user.service;

import com.example.auth.domain.user.repository.NotificationPreferenceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 수신 설정 파기를 담당한다(탈퇴 시 물리 삭제 — 수신 동의는 회원 종결과 함께 목적을 다한다).
 */
@Service
public class NotificationPreferenceRemover {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceRemover(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    /**
     * 회원의 수신 설정을 파기한다(미존재면 무시 — 멱등).
     */
    @Transactional
    public void purge(UUID userId) {
        repository.findById(userId).ifPresent(repository::delete);
    }
}
