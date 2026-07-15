package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 로그인 요청의 기기 식별 블록이다. 지문으로 기존 기기를 재식별하고, 신규면 이 정보로 등록한다.
 * 미지의 platform 값은 enum 역직렬화가 400으로 차단한다.
 */
public record DeviceBindingRequest(
        @NotBlank String fingerprint,
        @NotBlank String deviceName,
        @NotNull DevicePlatform platform) {}
