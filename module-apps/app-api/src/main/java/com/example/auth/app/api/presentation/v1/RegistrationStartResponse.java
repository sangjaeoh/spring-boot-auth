package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.info.RegistrationStartedInfo;
import java.util.UUID;

/**
 * 온보딩 시작 응답. {@code onboardingToken}은 이 응답에서 1회만 전달된다(서버는 해시만 보관).
 */
public record RegistrationStartResponse(
        UUID registrationId, String onboardingToken, String emailChallengeId, long expiresInSeconds) {

    public static RegistrationStartResponse from(RegistrationStartedInfo info) {
        return new RegistrationStartResponse(
                info.registrationId(), info.onboardingToken(), info.emailChallengeId(), info.expiresInSeconds());
    }
}
