package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 상태 비밀번호 변경 요청 DTO다.
 */
public record PasswordChangeRequest(
        @NotBlank String currentPassword, @NotBlank String newPassword) {}
