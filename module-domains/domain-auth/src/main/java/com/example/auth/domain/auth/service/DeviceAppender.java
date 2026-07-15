package com.example.auth.domain.auth.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.Device;
import com.example.auth.domain.auth.entity.DevicePlatform;
import com.example.auth.domain.auth.event.DeviceRegistered;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.DeviceRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 등록을 담당한다. 중복은 읽기 판정으로 409를 만들고, 경합 이중등록은 {@code (userId, fingerprint)}
 * DB 유니크 인덱스가 backstop한다.
 */
@Service
public class DeviceAppender {

    private final DeviceRepository deviceRepository;
    private final MessagePublisher messagePublisher;

    public DeviceAppender(DeviceRepository deviceRepository, MessagePublisher messagePublisher) {
        this.deviceRepository = deviceRepository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 새 기기를 등록하고 접속 사실을 기록한 뒤 {@link DeviceRegistered}를 발행한다.
     *
     * @throws AuthException 같은 지문의 기기가 이미 등록됐으면(409)
     */
    @Transactional
    public UUID register(
            UUID userId, String deviceName, DevicePlatform platform, String fingerprint, String ip, Instant now) {
        if (deviceRepository.findByUserIdAndFingerprint(userId, fingerprint).isPresent()) {
            throw new AuthException(AuthErrorCode.DEVICE_ALREADY_REGISTERED);
        }
        Device device = Device.create(userId, deviceName, platform, fingerprint);
        device.recordAccess(ip, now);
        deviceRepository.save(device);
        messagePublisher.publish(new DeviceRegistered(userId, device.getId(), now));
        return device.getId();
    }
}
