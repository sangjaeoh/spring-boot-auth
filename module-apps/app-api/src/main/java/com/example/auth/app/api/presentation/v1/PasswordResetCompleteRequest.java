package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호 재설정 완료 요청 DTO다.
 */
public record PasswordResetCompleteRequest(
        @NotBlank String challengeId,
        @NotBlank String code,
        @NotBlank String newPassword) {}
