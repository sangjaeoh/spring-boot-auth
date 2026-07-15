package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.user.info.MyUserInfo;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 내 정보 응답이다(이름·연락처·로그인 이메일은 마스킹 표시).
 */
public record MyInfoResponse(
        UUID userId,
        String name,
        LocalDate birthDate,
        String gender,
        String contactEmail,
        String contactPhone,
        String loginEmail,
        String status,
        Instant joinedAt) {

    /**
     * 유저 경계 모델과 인증의 로그인 이메일(마스킹)을 합성한다.
     */
    public static MyInfoResponse of(MyUserInfo info, String maskedLoginEmail) {
        return new MyInfoResponse(
                info.userId(),
                info.maskedName(),
                info.birthDate(),
                info.gender().name(),
                info.maskedContactEmail(),
                info.maskedContactPhone(),
                maskedLoginEmail,
                info.status().name(),
                info.joinedAt());
    }
}
