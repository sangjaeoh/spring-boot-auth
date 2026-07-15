package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.entity.FailureReason;
import com.example.auth.domain.auth.entity.LoginAttempt;
import com.example.auth.domain.auth.entity.LoginResult;
import com.example.auth.domain.auth.repository.LoginAttemptRepository;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시도를 이력으로 append한다.
 */
@Service
public class LoginAttemptAppender {

    private final LoginAttemptRepository repository;

    public LoginAttemptAppender(LoginAttemptRepository repository) {
        this.repository = repository;
    }

    /**
     * 로그인 시도 한 건을 불변 기록으로 저장한다.
     */
    @Transactional
    public void record(
            @Nullable UUID userId,
            LoginResult result,
            @Nullable FailureReason failureReason,
            String ip,
            @Nullable UUID deviceId,
            int riskScore,
            @Nullable String countryCode,
            Instant at) {
        repository.save(LoginAttempt.create(userId, result, failureReason, ip, deviceId, riskScore, countryCode, at));
    }
}
