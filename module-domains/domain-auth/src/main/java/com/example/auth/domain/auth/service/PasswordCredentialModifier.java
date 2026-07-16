package com.example.auth.domain.auth.service;

import com.example.auth.common.core.crypto.PasswordHasher;
import com.example.auth.common.messaging.MessagePublisher;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 비밀번호 변경·재설정을 수행한다(정책·최근 N 재사용 금지 강제).
 *
 * <p>KDF(현재 대조·재사용 대조·신규 인코딩)는 로그인 경로처럼 DB 트랜잭션 밖에서 수행한다 — KDF는 동시성
 * 상한 리미터를 거치므로 리미터 포화 시 슬롯 대기만큼 커넥션을 점유하지 않게 한다. 자격증명·이력 갱신만
 * 짧은 트랜잭션으로 원자 커밋하고, 변경은 커밋 직전 현재 해시가 대조 시점과 같은지 재확인해 KDF와 커밋
 * 사이의 동시 변경을 불일치로 거부한다(재설정은 챌린지가 인가라 최종 쓰기가 이긴다).
 */
@Service
public class PasswordCredentialModifier {

    private final PasswordCredentialRepository repository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyValidator policyValidator;
    private final MessagePublisher messagePublisher;
    private final TransactionTemplate transactionTemplate;
    private final int historyLimit;

    public PasswordCredentialModifier(
            PasswordCredentialRepository repository,
            PasswordHasher passwordHasher,
            PasswordPolicyValidator policyValidator,
            MessagePublisher messagePublisher,
            PlatformTransactionManager transactionManager,
            @Value("${auth.password.history-size:5}") int historyLimit) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.policyValidator = policyValidator;
        this.messagePublisher = messagePublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.historyLimit = historyLimit;
    }

    /**
     * 현재 비밀번호를 검증한 뒤 정책·재사용 금지를 통과한 새 비밀번호로 교체한다.
     *
     * @throws AuthException 자격증명 미존재; 현재 비밀번호 불일치(커밋 직전 동시 변경 감지 포함); 정책 위반;
     *     재사용
     */
    public void change(UUID userId, String currentRaw, String newRaw, Instant now) {
        String verifiedCurrentHash = getCredential(userId).getPasswordHash();
        if (!passwordHasher.matches(currentRaw, verifiedCurrentHash)) {
            throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        String newHash = encodeValidatedPassword(userId, newRaw);
        transactionTemplate.executeWithoutResult(status -> {
            PasswordCredential credential = getCredential(userId);
            if (!credential.getPasswordHash().equals(verifiedCurrentHash)) {
                throw new AuthException(AuthErrorCode.CURRENT_PASSWORD_MISMATCH);
            }
            credential.changePassword(newHash, HashAlgorithm.valueOf(passwordHasher.algorithm()), now, historyLimit);
            messagePublisher.publish(new PasswordChanged(userId, now));
        });
    }

    /**
     * 재설정 인증을 통과한 사용자의 비밀번호를 새 비밀번호로 교체한다(현재 비밀번호 검증 없음 — 챌린지가 인가).
     *
     * @throws AuthException 자격증명 미존재; 정책 위반; 재사용
     */
    public void resetTo(UUID userId, String newRaw, Instant now) {
        getCredential(userId);
        String newHash = encodeValidatedPassword(userId, newRaw);
        transactionTemplate.executeWithoutResult(status -> {
            PasswordCredential credential = getCredential(userId);
            credential.changePassword(newHash, HashAlgorithm.valueOf(passwordHasher.algorithm()), now, historyLimit);
            messagePublisher.publish(new PasswordResetCompleted(userId, now));
        });
    }

    /**
     * 정책·재사용 금지를 검증하고 새 비밀번호를 인코딩해 반환한다(트랜잭션 밖 KDF 구간).
     */
    private String encodeValidatedPassword(UUID userId, String newRaw) {
        policyValidator.validate(newRaw);
        for (String historicHash : repository.findHistoryHashes(userId)) {
            if (passwordHasher.matches(newRaw, historicHash)) {
                throw new AuthException(AuthErrorCode.PASSWORD_REUSED);
            }
        }
        return passwordHasher.hash(newRaw);
    }

    private PasswordCredential getCredential(UUID userId) {
        return repository
                .findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.PASSWORD_CREDENTIAL_NOT_FOUND));
    }
}
