package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.IdentityVerification;
import com.example.auth.domain.user.entity.Provider;
import com.example.auth.domain.user.repository.IdentityVerificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실명확인 요청 레코드 생성을 담당한다(기관 호출 전에 REQUESTED를 확정 — 감사·만료 스윕의 기준점).
 */
@Service
public class IdentityVerificationAppender {

    private final IdentityVerificationRepository repository;
    private final Duration progressTtl;

    public IdentityVerificationAppender(
            IdentityVerificationRepository repository,
            @Value("${user.identity-verification.ttl-minutes:10}") long ttlMinutes) {
        this.repository = repository;
        this.progressTtl = Duration.ofMinutes(ttlMinutes);
    }

    /**
     * 실명확인 요청(REQUESTED)을 생성하고 {@code VerificationId}를 반환한다.
     */
    @Transactional
    public UUID request(Provider provider, Instant now) {
        IdentityVerification verification = IdentityVerification.create(provider, now, now.plus(progressTtl));
        repository.save(verification);
        return verification.getId();
    }
}
