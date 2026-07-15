package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.entity.DevicePlatform;
import com.example.auth.domain.auth.info.DeviceInfo;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 내 기기 목록 응답 행이다.
 */
public record DeviceResponse(
        UUID deviceId,
        String deviceName,
        DevicePlatform platform,
        @Nullable String lastIp,
        @Nullable Instant lastAccessedAt,
        boolean trusted,
        boolean pushEnabled) {

    public static DeviceResponse from(DeviceInfo info) {
        return new DeviceResponse(
                info.deviceId(),
                info.deviceName(),
                info.platform(),
                info.lastIp(),
                info.lastAccessedAt(),
                info.trusted(),
                info.pushEnabled());
    }
}
