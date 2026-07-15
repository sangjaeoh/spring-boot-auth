package com.example.auth.domain.user.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.user.entity.IdentityVerification;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.event.UserWithdrawn;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.CiRegistryRepository;
import com.example.auth.domain.user.repository.IdentityVerificationRepository;
import com.example.auth.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴를 수행한다 — 한 트랜잭션에서 {@code User.withdraw}(WITHDRAWN + PII 즉시 파기) →
 * {@code CiRegistry.retainOnWithdrawal}(tombstone 분리보관) → 본인인증 결과 PII 파기 → 수신 설정 파기를
 * 원자 수행하고 {@code UserWithdrawn}을 발행한다.
 *
 * <p>{@code CreateUser}와 같은 근거의 문서화된 다중 애그리거트 트랜잭션 예외다 — 파기와 tombstone이
 * 원자적이어야 부분 파기 상태가 남지 않는다. 법정 보존분(동의이력 5년·탈퇴 시각)은 파기하지 않는다.
 * 인증 측 정리(세션 전멸·자격증명/소셜/기기)는 발행된 {@code UserWithdrawn}의 소비가 수행한다.
 */
@Service
public class UserWithdrawalProcessor {

    private final UserRepository userRepository;
    private final CiRegistryRepository ciRegistryRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final NotificationPreferenceRemover notificationPreferenceRemover;
    private final MessagePublisher messagePublisher;

    public UserWithdrawalProcessor(
            UserRepository userRepository,
            CiRegistryRepository ciRegistryRepository,
            IdentityVerificationRepository identityVerificationRepository,
            NotificationPreferenceRemover notificationPreferenceRemover,
            MessagePublisher messagePublisher) {
        this.userRepository = userRepository;
        this.ciRegistryRepository = ciRegistryRepository;
        this.identityVerificationRepository = identityVerificationRepository;
        this.notificationPreferenceRemover = notificationPreferenceRemover;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 회원을 탈퇴 처리하고 스냅샷 동기화용 단조 {@code statusVersion}을 반환한다.
     *
     * @throws UserException 미존재 회원이면(404), 이미 탈퇴한 회원이면(409)
     */
    @Transactional
    public long withdraw(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        Instant now = Instant.now();
        user.withdraw(now);
        ciRegistryRepository.findByLinkedUserId(userId).ifPresent(registry -> registry.retainOnWithdrawal(now));
        for (IdentityVerification verification : identityVerificationRepository.findByUserId(userId)) {
            verification.purgeResult();
        }
        notificationPreferenceRemover.purge(userId);
        messagePublisher.publish(new UserWithdrawn(userId, user.getStatusVersion(), now));
        return user.getStatusVersion();
    }
}
