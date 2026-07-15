package com.example.auth.domain.auth.info;

import com.example.auth.domain.auth.entity.Device;
import com.example.auth.domain.auth.entity.DevicePlatform;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 기기 경계 조회 모델이다(지문은 재식별 내부 키라 경계로 내보내지 않는다).
 */
public record DeviceInfo(
        UUID deviceId,
        String deviceName,
        DevicePlatform platform,
        @Nullable String lastIp,
        @Nullable Instant lastAccessedAt,
        boolean trusted,
        boolean pushEnabled) {

    public static DeviceInfo from(Device device) {
        return new DeviceInfo(
                device.getId(),
                device.getDeviceName(),
                device.getPlatform(),
                device.getLastIp(),
                device.getLastAccessedAt(),
                device.isTrusted(),
                device.isPushEnabled());
    }
}
