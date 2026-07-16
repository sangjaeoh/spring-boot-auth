package com.example.auth.app.admin.infrastructure.query;

import com.example.auth.domain.auth.entity.FailureReason;
import com.example.auth.domain.auth.entity.LoginAttempt;
import com.example.auth.domain.auth.entity.LoginResult;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 로그인 이력 행이다(격리 구역 내부 변환).
 */
public record LoginAttemptRow(
        UUID id,
        LoginResult result,
        @Nullable FailureReason failureReason,
        String ip,
        @Nullable UUID deviceId,
        int riskScore,
        @Nullable String countryCode,
        Instant at) {

    public static LoginAttemptRow from(LoginAttempt attempt) {
        return new LoginAttemptRow(
                attempt.getId(),
                attempt.getResult(),
                attempt.getFailureReason(),
                attempt.getIp(),
                attempt.getDeviceId(),
                attempt.getRiskScore(),
                attempt.getCountryCode(),
                attempt.getAt());
    }
}
