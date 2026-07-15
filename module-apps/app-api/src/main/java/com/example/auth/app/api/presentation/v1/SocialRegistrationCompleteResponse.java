package com.example.auth.app.api.presentation.v1;

import java.util.UUID;

/**
 * SOCIAL 가입 완료 응답. 생성(재실행이면 기존)된 회원의 {@code UserId}와 즉시 발급된 로그인 세션
 * 토큰이다.
 */
public record SocialRegistrationCompleteResponse(
        UUID userId, String accessToken, String refreshToken, long expiresInSeconds) {}
