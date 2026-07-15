package com.example.auth.domain.auth.port;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 저장소가 반환하는 활성 세션 스냅샷이다.
 */
public record SessionSnapshot(
        UUID sessionId,
        UUID deviceId,
        String ip,
        @Nullable String userAgent,
        Instant issuedAt,
        Instant lastAccessedAt) {}
