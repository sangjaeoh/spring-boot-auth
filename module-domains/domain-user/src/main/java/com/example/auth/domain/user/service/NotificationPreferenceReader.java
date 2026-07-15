package com.example.auth.domain.user.service;

import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.info.NotificationPreferenceInfo;
import com.example.auth.domain.user.repository.NotificationPreferenceRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 수신 설정 조회를 담당한다.
 */
@Service
public class NotificationPreferenceReader {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceReader(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    /**
     * 수신 설정 매트릭스를 반환한다.
     *
     * @throws UserException 설정이 존재하지 않으면(404)
     */
    @Transactional(readOnly = true)
    public NotificationPreferenceInfo get(UUID userId) {
        return repository
                .findById(userId)
                .map(NotificationPreferenceInfo::from)
                .orElseThrow(() -> new UserException(UserErrorCode.NOTIFICATION_PREFERENCE_NOT_FOUND));
    }

    /**
     * 수신 설정 매트릭스를 반환한다 — 발송 판정 경로용. 미존재(탈퇴 정리·과거 회원)는 빈 값으로
     * 반환해 호출측이 기본 정책을 적용한다.
     */
    @Transactional(readOnly = true)
    public Optional<NotificationPreferenceInfo> find(UUID userId) {
        return repository.findById(userId).map(NotificationPreferenceInfo::from);
    }
}
