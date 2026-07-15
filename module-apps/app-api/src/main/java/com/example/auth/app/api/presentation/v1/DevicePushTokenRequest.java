package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.entity.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 푸시 토큰 등록 요청이다(재등록은 교체).
 */
public record DevicePushTokenRequest(
        @NotBlank String token, @NotNull PushPlatform platform) {}
