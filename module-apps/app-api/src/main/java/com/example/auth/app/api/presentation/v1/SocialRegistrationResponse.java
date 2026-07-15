package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.info.SocialRegistrationStartedInfo;
import java.util.UUID;

/**
 * SOCIAL 온보딩 시작 응답. {@code onboardingToken}은 이 응답에서 1회만 전달된다(서버는 해시만 보관).
 * 잔여 스텝은 본인인증과 필수 약관 동의다(기존 {@code /auth/registration} 스텝 엔드포인트 사용).
 */
public record SocialRegistrationResponse(UUID registrationId, String onboardingToken, long expiresInSeconds) {

    public static SocialRegistrationResponse from(SocialRegistrationStartedInfo info) {
        return new SocialRegistrationResponse(info.registrationId(), info.onboardingToken(), info.expiresInSeconds());
    }
}
