package com.example.auth.domain.auth.service;

import com.example.auth.common.core.crypto.PasswordHasher;
import com.example.auth.domain.auth.entity.HashAlgorithm;
import com.example.auth.domain.auth.entity.PasswordCredential;
import com.example.auth.domain.auth.repository.PasswordCredentialRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 자격증명 생성을 담당한다.
 */
@Service
public class PasswordCredentialAppender {

    private final PasswordCredentialRepository repository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicyValidator policyValidator;

    public PasswordCredentialAppender(
            PasswordCredentialRepository repository,
            PasswordHasher passwordHasher,
            PasswordPolicyValidator policyValidator) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.policyValidator = policyValidator;
    }

    /**
     * 정책 검증 후 원문을 안전 해시로 인코딩해 자격증명을 등록한다.
     */
    @Transactional
    public void register(UUID userId, String rawPassword, Instant now) {
        policyValidator.validate(rawPassword);
        String hash = passwordHasher.hash(rawPassword);
        HashAlgorithm algorithm = HashAlgorithm.valueOf(passwordHasher.algorithm());
        repository.save(PasswordCredential.create(userId, hash, algorithm, now));
    }
}
