package com.example.auth.domain.auth.info;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 리프레시 회전 요청의 처리 결과다(경계 반환).
 *
 * <p>{@code ROTATED}=새 토큰 발급(유예 재시도 포함), {@code REUSE_DETECTED}=탈취 감지로 세션 전멸,
 * {@code INVALID}=무효 토큰. 회전 시에만 {@code refreshToken}·식별자가 채워진다.
 */
public record RotationOutcome(
        Outcome outcome,
        @Nullable UUID userId,
        @Nullable UUID sessionId,
        @Nullable String refreshToken) {

    public enum Outcome {
        ROTATED,
        REUSE_DETECTED,
        INVALID
    }

    public static RotationOutcome rotated(UUID userId, UUID sessionId, String refreshToken) {
        return new RotationOutcome(Outcome.ROTATED, userId, sessionId, refreshToken);
    }

    public static RotationOutcome reuseDetected() {
        return new RotationOutcome(Outcome.REUSE_DETECTED, null, null, null);
    }

    public static RotationOutcome invalid() {
        return new RotationOutcome(Outcome.INVALID, null, null, null);
    }
}
