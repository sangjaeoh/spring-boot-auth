package com.example.auth.app.api.presentation.v1;

/**
 * Access·Refresh 토큰 응답 DTO다.
 */
public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds) {}
