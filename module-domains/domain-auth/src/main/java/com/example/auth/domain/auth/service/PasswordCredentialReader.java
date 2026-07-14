package com.example.auth.domain.auth.service;

import com.example.auth.common.core.crypto.PasswordHasher;
import com.example.auth.domain.auth.repository.PasswordCredentialRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 대조를 담당한다(부작용 없음).
 *
 * <p>계정 미존재 경로도 더미 해시 검증을 태워 응답 타이밍으로 계정 존재를 유추하지 못하게 한다(열거 저항).
 * KDF 연산은 DB 트랜잭션 밖에서 수행해 커넥션 점유를 피한다.
 */
@Service
public class PasswordCredentialReader {

    private final PasswordCredentialRepository repository;
    private final PasswordHasher passwordHasher;
    private final String dummyHash;

    public PasswordCredentialReader(PasswordCredentialRepository repository, PasswordHasher passwordHasher) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.dummyHash = passwordHasher.hash("timing-parity-placeholder");
    }

    /**
     * 사용자의 비밀번호가 일치하는지 대조한다. 자격증명이 없으면 더미 검증 후 false를 반환한다.
     */
    public boolean verify(UUID userId, String rawPassword) {
        return repository
                .findById(userId)
                .map(credential -> passwordHasher.matches(rawPassword, credential.getPasswordHash()))
                .orElseGet(() -> {
                    passwordHasher.matches(rawPassword, dummyHash);
                    return false;
                });
    }

    /**
     * 계정이 존재하지 않는 로그인 경로의 타이밍 패리티용 더미 검증을 수행한다.
     */
    public void verifyAbsent(String rawPassword) {
        passwordHasher.matches(rawPassword, dummyHash);
    }
}
