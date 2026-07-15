package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.info.MyUserInfo;
import com.example.auth.domain.user.info.NotificationRecipientInfo;
import com.example.auth.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 조회를 담당한다.
 */
@Service
public class UserReader {

    private final UserRepository repository;

    public UserReader(UserRepository repository) {
        this.repository = repository;
    }

    /**
     * 내 정보를 마스킹 적용해 반환한다. 탈퇴 회원은 PII가 파기된 상태라 미존재로 취급한다.
     *
     * @throws UserException 미존재·탈퇴 회원이면(404)
     */
    @Transactional(readOnly = true)
    public MyUserInfo getMe(UUID userId) {
        User user = repository
                .findById(userId)
                .filter(found -> found.getStatus() != LifecycleStatus.WITHDRAWN)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        return MyUserInfo.from(user);
    }

    /**
     * 연락용 이메일 원문을 반환한다(알림·OTP 수신자 진실원본 — 표시 마스킹은 호출측 정책).
     *
     * @throws UserException 미존재·탈퇴(PII 파기) 회원이면(404)
     */
    @Transactional(readOnly = true)
    public String getContactEmail(UUID userId) {
        return repository
                .findById(userId)
                .map(User::getContact)
                .map(contact -> contact.contactEmail().value())
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    /**
     * 알림 수신자 연락처(이메일·전화 원문)를 반환한다 — 발송 판정 경로용. 미존재·탈퇴(PII 파기)
     * 회원은 빈 값으로 반환해 호출측이 발송을 스킵한다.
     */
    @Transactional(readOnly = true)
    public Optional<NotificationRecipientInfo> findNotificationRecipient(UUID userId) {
        return repository.findById(userId).map(User::getContact).map(NotificationRecipientInfo::from);
    }
}
