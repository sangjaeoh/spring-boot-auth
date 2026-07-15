package com.example.auth.app.api.presentation.v1;

import org.jspecify.annotations.Nullable;

/**
 * 소셜 로그인 응답 — 기존 연동이면 즉시 세션({@code LOGGED_IN} + {@code token}), 미연동 신규면 SOCIAL
 * 온보딩 유도({@code REGISTRATION_REQUIRED} + {@code registration})다.
 */
public record SocialLoginResponse(
        Status status,
        @Nullable TokenResponse token,
        @Nullable SocialRegistrationResponse registration) {

    public enum Status {
        LOGGED_IN,
        REGISTRATION_REQUIRED
    }

    public static SocialLoginResponse loggedIn(TokenResponse token) {
        return new SocialLoginResponse(Status.LOGGED_IN, token, null);
    }

    public static SocialLoginResponse registrationRequired(SocialRegistrationResponse registration) {
        return new SocialLoginResponse(Status.REGISTRATION_REQUIRED, null, registration);
    }
}
