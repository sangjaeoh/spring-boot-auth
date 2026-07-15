package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.entity.DevicePlatform;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 내 세션 목록 응답 행이다. 기기명·플랫폼은 기기 목록과의 조인 결과라, 기기 삭제 직후의 짧은 정리
 * 지연 동안 비어 있을 수 있다.
 */
public record SessionResponse(
        UUID sessionId,
        UUID deviceId,
        @Nullable String deviceName,
        @Nullable DevicePlatform platform,
        String ip,
        Instant lastAccessedAt,
        boolean current) {}
