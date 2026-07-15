package com.example.auth.app.api.facade;

import com.example.auth.app.api.presentation.v1.DeviceRegisterRequest;
import com.example.auth.app.api.presentation.v1.DeviceResponse;
import com.example.auth.domain.auth.entity.PushPlatform;
import com.example.auth.domain.auth.service.DeviceAppender;
import com.example.auth.domain.auth.service.DeviceModifier;
import com.example.auth.domain.auth.service.DeviceReader;
import com.example.auth.domain.auth.service.DeviceRemover;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 기기 등록·목록·신뢰·푸시 타겟·등록 해제를 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는
 * 열지 않는다). 기기 삭제에 따른 해당 기기 세션 종료는 {@code DeviceDeleted} 소비 리스너가 수행한다.
 */
@Component
public class DeviceFacade {

    private final DeviceAppender deviceAppender;
    private final DeviceReader deviceReader;
    private final DeviceModifier deviceModifier;
    private final DeviceRemover deviceRemover;

    public DeviceFacade(
            DeviceAppender deviceAppender,
            DeviceReader deviceReader,
            DeviceModifier deviceModifier,
            DeviceRemover deviceRemover) {
        this.deviceAppender = deviceAppender;
        this.deviceReader = deviceReader;
        this.deviceModifier = deviceModifier;
        this.deviceRemover = deviceRemover;
    }

    /**
     * 기기를 명시 등록한다. 같은 지문의 기기가 이미 있으면 409다.
     */
    public UUID register(UUID userId, DeviceRegisterRequest request, String ip) {
        return deviceAppender.register(
                userId, request.deviceName(), request.platform(), request.fingerprint(), ip, Instant.now());
    }

    /**
     * 내 기기 목록을 최근 접속 순으로 반환한다.
     */
    public List<DeviceResponse> devices(UUID userId) {
        return deviceReader.findDevices(userId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    /**
     * 기기를 신뢰로 표시한다.
     */
    public void trust(UUID userId, UUID deviceId) {
        deviceModifier.trust(userId, deviceId);
    }

    /**
     * 기기 신뢰를 해제한다.
     */
    public void untrust(UUID userId, UUID deviceId) {
        deviceModifier.untrust(userId, deviceId);
    }

    /**
     * 푸시 토큰과 전송 플랫폼을 등록한다.
     */
    public void registerPushToken(UUID userId, UUID deviceId, String token, PushPlatform platform) {
        deviceModifier.registerPushToken(userId, deviceId, token, platform);
    }

    /**
     * OS 푸시 권한 상태를 반영한다.
     */
    public void reflectPushPermission(UUID userId, UUID deviceId, boolean enabled) {
        deviceModifier.reflectPushPermission(userId, deviceId, enabled);
    }

    /**
     * 기기를 등록 해제한다. 해당 기기의 세션은 {@code DeviceDeleted} 소비로 종료된다.
     */
    public void deregister(UUID userId, UUID deviceId) {
        deviceRemover.deregister(userId, deviceId);
    }
}
