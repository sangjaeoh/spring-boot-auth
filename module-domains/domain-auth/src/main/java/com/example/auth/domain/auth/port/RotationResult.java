package com.example.auth.domain.auth.port;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 리프레시 회전 원자 연산의 판정 결과다({@link SessionStore#rotate}).
 *
 * <p>{@code ROTATED}=정상 회전, {@code GRACE_REPLAY}=유예 창 내 재시도(직전 회전이 발급한 동일 토큰 멱등
 * 재반환), {@code REUSE}=유예 밖 재사용(패밀리 전체 무효화 완료), {@code INVALID}=미해결 토큰.
 */
public record RotationResult(
        RotationType type,
        @Nullable UUID userId,
        @Nullable UUID sessionId,
        @Nullable String refreshPlain) {

    public enum RotationType {
        ROTATED,
        GRACE_REPLAY,
        REUSE,
        INVALID
    }

    public static RotationResult rotated(UUID userId, UUID sessionId, String refreshPlain) {
        return new RotationResult(RotationType.ROTATED, userId, sessionId, refreshPlain);
    }

    public static RotationResult graceReplay(UUID userId, UUID sessionId, String refreshPlain) {
        return new RotationResult(RotationType.GRACE_REPLAY, userId, sessionId, refreshPlain);
    }

    public static RotationResult reuse(UUID userId, UUID sessionId) {
        return new RotationResult(RotationType.REUSE, userId, sessionId, null);
    }

    public static RotationResult invalid() {
        return new RotationResult(RotationType.INVALID, null, null, null);
    }
}
