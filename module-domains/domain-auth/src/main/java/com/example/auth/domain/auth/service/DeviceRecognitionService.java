package com.example.auth.domain.auth.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.Device;
import com.example.auth.domain.auth.entity.DevicePlatform;
import com.example.auth.domain.auth.event.DeviceRegistered;
import com.example.auth.domain.auth.event.NewDeviceDetected;
import com.example.auth.domain.auth.info.RecognizedDeviceInfo;
import com.example.auth.domain.auth.repository.DeviceRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 기기 인식이다(세션 생성 전 — DOMAIN_MODEL §2.5). 지문으로 기존 기기를 매칭해 접속 사실을
 * 갱신하고, 신규 기기면 등록 후 {@link DeviceRegistered}·{@link NewDeviceDetected}를 발행한다.
 *
 * <p>이름은 DOMAIN_MODEL §11 서비스 표의 명명을 따른다({@code Service} 접미사 예외).
 */
@Service
public class DeviceRecognitionService {

    private final DeviceRepository deviceRepository;
    private final MessagePublisher messagePublisher;

    public DeviceRecognitionService(DeviceRepository deviceRepository, MessagePublisher messagePublisher) {
        this.deviceRepository = deviceRepository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 지문으로 기기를 인식해 세션에 바인딩할 기기와 신규 여부를 반환한다. 같은 지문의 동시 최초
     * 로그인 경합은 {@code (userId, fingerprint)} DB 유니크가 한쪽을 거부한다(재시도로 수렴).
     */
    @Transactional
    public RecognizedDeviceInfo recognize(
            UUID userId, String fingerprint, String deviceName, DevicePlatform platform, String ip, Instant now) {
        return deviceRepository
                .findByUserIdAndFingerprint(userId, fingerprint)
                .map(existing -> {
                    existing.recordAccess(ip, now);
                    return new RecognizedDeviceInfo(existing.getId(), false);
                })
                .orElseGet(() -> {
                    Device device = Device.create(userId, deviceName, platform, fingerprint);
                    device.recordAccess(ip, now);
                    deviceRepository.save(device);
                    messagePublisher.publish(new DeviceRegistered(userId, device.getId(), now));
                    messagePublisher.publish(new NewDeviceDetected(userId, device.getId(), now));
                    return new RecognizedDeviceInfo(device.getId(), true);
                });
    }
}
