package com.example.auth.domain.user.info;

import static java.util.Objects.requireNonNull;

import com.example.auth.common.core.masking.PiiMasker;
import com.example.auth.domain.user.entity.Contact;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.Profile;
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
     * 회원 엔티티를 마스킹 적용된 내 정보 모델로 변환한다. PII가 파기된 탈퇴 회원은 변환 대상이 아니다
     * (조회 경로가 WITHDRAWN을 미존재로 걸러낸다).
     */
    public static MyUserInfo from(User user) {
        Profile profile = requireNonNull(user.getProfile());
        Contact contact = requireNonNull(user.getContact());
        return new MyUserInfo(
                user.getId(),
                PiiMasker.maskName(profile.name()),
                profile.birthDate(),
                profile.gender(),
                PiiMasker.maskEmail(contact.contactEmail().value()),
                PiiMasker.maskPhone(contact.contactPhone().number()),
                user.getStatus(),
                user.getCreatedAt());
    }
}
