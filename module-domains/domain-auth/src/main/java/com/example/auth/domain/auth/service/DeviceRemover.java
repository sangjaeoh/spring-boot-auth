package com.example.auth.domain.auth.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.Device;
import com.example.auth.domain.auth.event.DeviceDeleted;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.DeviceRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 등록 해제를 담당한다. 물리 삭제인 이유는 엔티티 Javadoc이 소유한다. 해당 기기 세션 종료는
 * {@link DeviceDeleted} 소비 측이 수행한다(커밋 후 전달·실패 시 DLQ 재시도로 유실 없이 수렴).
 */
@Service
public class DeviceRemover {

    private final DeviceRepository deviceRepository;
    private final MessagePublisher messagePublisher;

    public DeviceRemover(DeviceRepository deviceRepository, MessagePublisher messagePublisher) {
        this.deviceRepository = deviceRepository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 기기를 등록 해제하고 {@link DeviceDeleted}를 발행한다.
     *
     * @throws AuthException 기기가 없으면(404)
     */
    @Transactional
    public void deregister(UUID userId, UUID deviceId) {
        Device device = deviceRepository
                .findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.DEVICE_NOT_FOUND));
        deviceRepository.delete(device);
        messagePublisher.publish(new DeviceDeleted(userId, deviceId, Instant.now()));
    }

    /**
     * 회원의 모든 기기를 파기한다(탈퇴 정리 — 없으면 무시·멱등). 세션은 탈퇴 정리가 전멸시키므로
     * 기기별 {@link DeviceDeleted}는 발행하지 않는다.
     */
    @Transactional
    public void purgeAll(UUID userId) {
        deviceRepository.deleteByUserId(userId);
    }
}
