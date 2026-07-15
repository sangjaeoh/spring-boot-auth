package com.example.auth.domain.auth.info;

import static java.util.Objects.requireNonNull;

import com.example.auth.domain.auth.entity.Device;
import java.util.UUID;

/**
 * 푸시 발송 타겟(기기·토큰)의 경계 조회 모델이다 — 발송 판정 경로 전용.
 *
 * <p>{@code pushToken}은 발송 주소로 쓰는 원문이다. 표시용 기기 조회({@code DeviceInfo})는 토큰을
 * 노출하지 않는다.
 */
public record PushTargetInfo(UUID deviceId, String pushToken) {

    public static PushTargetInfo from(Device device) {
        return new PushTargetInfo(device.getId(), requireNonNull(device.getPushToken()));
    }
}
