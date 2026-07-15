package com.example.auth.domain.auth.info;

import com.example.auth.domain.auth.port.SessionSnapshot;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 활성 세션 경계 조회 모델이다(내 세션 목록).
 */
public record ActiveSessionInfo(
        UUID sessionId,
        UUID deviceId,
        String ip,
        @Nullable String userAgent,
        Instant issuedAt,
        Instant lastAccessedAt) {

    public static ActiveSessionInfo from(SessionSnapshot snapshot) {
        return new ActiveSessionInfo(
                snapshot.sessionId(),
                snapshot.deviceId(),
                snapshot.ip(),
                snapshot.userAgent(),
                snapshot.issuedAt(),
                snapshot.lastAccessedAt());
    }
}
