package com.example.auth.app.api.presentation.v1;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 로그인 요청 DTO다. 세션은 기기에 바인딩되므로 기기 식별 블록이 필수다.
 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        @Valid @NotNull DeviceBindingRequest device) {}
