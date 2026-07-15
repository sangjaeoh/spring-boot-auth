package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.entity.Device;
import com.example.auth.domain.auth.entity.PushPlatform;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.DeviceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 속성 변경(신뢰·푸시 타겟)을 담당한다. 소유자 검증은 {@code (id, userId)} 조회가 겸한다 —
 * 타인 기기는 미존재와 구분 없이 404다.
 */
@Service
public class DeviceModifier {

    private final DeviceRepository deviceRepository;

    public DeviceModifier(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * 기기를 신뢰로 표시한다.
     *
     * @throws AuthException 기기가 없으면(404)
     */
    @Transactional
    public void trust(UUID userId, UUID deviceId) {
        getOwned(userId, deviceId).trust();
    }

    /**
     * 기기 신뢰를 해제한다.
     *
     * @throws AuthException 기기가 없으면(404)
     */
    @Transactional
    public void untrust(UUID userId, UUID deviceId) {
        getOwned(userId, deviceId).untrust();
    }

    /**
     * 푸시 토큰과 전송 플랫폼을 등록한다(재등록은 교체).
     *
     * @throws AuthException 기기가 없으면(404)
     */
    @Transactional
    public void registerPushToken(UUID userId, UUID deviceId, String token, PushPlatform platform) {
        getOwned(userId, deviceId).registerPushToken(token, platform);
    }

    /**
     * OS 푸시 권한 상태를 반영한다.
     *
     * @throws AuthException 기기가 없으면(404)
     */
    @Transactional
    public void reflectPushPermission(UUID userId, UUID deviceId, boolean enabled) {
        getOwned(userId, deviceId).reflectPushPermission(enabled);
    }

    private Device getOwned(UUID userId, UUID deviceId) {
        return deviceRepository
                .findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.DEVICE_NOT_FOUND));
    }
}
