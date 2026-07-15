package com.example.auth.domain.auth.service;

import com.example.auth.common.core.crypto.PasswordHasher;
import com.example.auth.domain.auth.entity.HashAlgorithm;
import com.example.auth.domain.auth.entity.PasswordCredential;
import com.example.auth.domain.auth.event.PasswordChanged;
import com.example.auth.domain.auth.event.PasswordResetCompleted;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.PasswordCredentialRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경·재설정을 수행한다(정책·최근 N 재사용 금지 강제).
 *
 * <p>변경·재설정은 저빈도 조작이라 KDF(현재 대조·재사용 대조·신규 인코딩)를 트랜잭션 안에서 수행하고
 * 자격증명과 이력을 한 트랜잭션에 원자적으로 갱신한다. KDF는 동시성 상한 리미터를 거치므로 로그인 폭주로
 * 리미터가 포화되면 슬롯 대기만큼 커넥션을 점유할 수 있으나, 변경·재설정은 동시 실행 수가 작아 소모 커넥션이
 * 유계라 수용한다.
 */
@Service
public class PasswordCredentialModifier {

    private final PasswordCredentialRepository repository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyValidator policyValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final int historyLimit;

    public PasswordCredentialModifier(
            PasswordCredentialRepository repository,
            PasswordHasher passwordHasher,
            PasswordPolicyValidator policyValidator,
            ApplicationEventPublisher eventPublisher,
            @Value("${auth.password.history-size:5}") int historyLimit) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.policyValidator = policyValidator;
        this.eventPublisher = eventPublisher;
        this.historyLimit = historyLimit;
    }

    /**
     * 현재 비밀번호를 검증한 뒤 정책·재사용 금지를 통과한 새 비밀번호로 교체한다.
     *
     * @throws AuthException 자격증명 미존재; 현재 비밀번호 불일치; 정책 위반; 재사용
     */
    @Transactional
    public void change(UUID userId, String currentRaw, String newRaw, Instant now) {
        PasswordCredential credential = getCredential(userId);
        if (!passwordHasher.matches(currentRaw, credential.getPasswordHash())) {
            throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        applyNewPassword(credential, newRaw, now);
        eventPublisher.publishEvent(new PasswordChanged(userId));
    }

    /**
     * 재설정 인증을 통과한 사용자의 비밀번호를 새 비밀번호로 교체한다(현재 비밀번호 검증 없음 — 챌린지가 인가).
     *
     * @throws AuthException 자격증명 미존재; 정책 위반; 재사용
     */
    @Transactional
    public void resetTo(UUID userId, String newRaw, Instant now) {
        PasswordCredential credential = getCredential(userId);
        applyNewPassword(credential, newRaw, now);
        eventPublisher.publishEvent(new PasswordResetCompleted(userId));
    }

    private void applyNewPassword(PasswordCredential credential, String newRaw, Instant now) {
        policyValidator.validate(newRaw);
        rejectIfReused(credential, newRaw);
        String newHash = passwordHasher.hash(newRaw);
        credential.changePassword(newHash, HashAlgorithm.valueOf(passwordHasher.algorithm()), now, historyLimit);
    }

    private void rejectIfReused(PasswordCredential credential, String newRaw) {
        for (String historicHash : credential.historyHashes()) {
            if (passwordHasher.matches(newRaw, historicHash)) {
                throw new AuthException(AuthErrorCode.PASSWORD_REUSED);
            }
        }
    }

    private PasswordCredential getCredential(UUID userId) {
        return repository
                .findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.PASSWORD_CREDENTIAL_NOT_FOUND));
    }
}
