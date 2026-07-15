package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.info.DeviceInfo;
import com.example.auth.domain.auth.info.PushTargetInfo;
import com.example.auth.domain.auth.repository.DeviceRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 조회를 담당한다.
 */
@Service
public class DeviceReader {

    private final DeviceRepository deviceRepository;

    public DeviceReader(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * 사용자의 등록 기기 전체를 최근 접속 순으로 반환한다.
     */
    @Transactional(readOnly = true)
    public List<DeviceInfo> findDevices(UUID userId) {
        return deviceRepository.findAllByUserIdOrderByLastAccessedAtDesc(userId).stream()
                .map(DeviceInfo::from)
                .toList();
    }

    /**
     * 푸시 발송 가능 타겟(권한 허용 ∧ 토큰 존재)을 최근 접속 순으로 반환한다 — 발송 판정 경로 전용.
     */
    @Transactional(readOnly = true)
    public List<PushTargetInfo> findPushTargets(UUID userId) {
        return deviceRepository
                .findAllByUserIdAndPushEnabledTrueAndPushTokenIsNotNullOrderByLastAccessedAtDesc(userId)
                .stream()
                .map(PushTargetInfo::from)
                .toList();
    }
}
