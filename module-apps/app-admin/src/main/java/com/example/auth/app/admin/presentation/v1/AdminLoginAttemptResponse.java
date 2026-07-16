package com.example.auth.app.admin.presentation.v1;

import com.example.auth.app.admin.infrastructure.query.LoginAttemptRow;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 로그인 이력 응답이다.
 */
public record AdminLoginAttemptResponse(
        UUID id,
        String result,
        @Nullable String failureReason,
        String ip,
        @Nullable UUID deviceId,
        int riskScore,
        @Nullable String countryCode,
        Instant at) {

    public static AdminLoginAttemptResponse from(LoginAttemptRow row) {
        return new AdminLoginAttemptResponse(
                row.id(),
                row.result().name(),
                row.failureReason() == null ? null : row.failureReason().name(),
                row.ip(),
                row.deviceId(),
                row.riskScore(),
                row.countryCode(),
                row.at());
    }
}
