package com.example.auth.domain.user.info;

import com.example.auth.common.core.masking.PiiMasker;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 내 정보 경계 조회 모델이다 — 이름·연락처는 마스킹 정책을 적용해 원문을 경계 밖으로 내보내지 않는다.
 */
public record MyUserInfo(
        UUID userId,
        String maskedName,
        LocalDate birthDate,
        Gender gender,
        String maskedContactEmail,
        String maskedContactPhone,
        LifecycleStatus status,
        Instant joinedAt) {

    /**
     * 회원 엔티티를 마스킹 적용된 내 정보 모델로 변환한다.
     */
    public static MyUserInfo from(User user) {
        return new MyUserInfo(
                user.getId(),
                PiiMasker.maskName(user.getProfile().name()),
                user.getProfile().birthDate(),
                user.getProfile().gender(),
                PiiMasker.maskEmail(user.getContact().contactEmail().value()),
                PiiMasker.maskPhone(user.getContact().contactPhone().number()),
                user.getStatus(),
                user.getCreatedAt());
    }
}
