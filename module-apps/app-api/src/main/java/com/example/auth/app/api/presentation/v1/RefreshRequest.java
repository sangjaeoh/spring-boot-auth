package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotBlank;

/**
 * 토큰 재발급 요청 DTO다.
 */
public record RefreshRequest(@NotBlank String refreshToken) {}
