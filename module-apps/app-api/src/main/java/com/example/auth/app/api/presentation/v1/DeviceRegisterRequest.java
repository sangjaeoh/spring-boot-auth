package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 기기 명시 등록 요청이다(로그인 시 자동 등록과 별개 진입점).
 */
public record DeviceRegisterRequest(
        @NotBlank String fingerprint,
        @NotBlank String deviceName,
        @NotNull DevicePlatform platform) {}
