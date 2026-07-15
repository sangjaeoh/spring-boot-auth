package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호 재설정 시작 요청 DTO다.
 */
public record PasswordResetInitiateRequest(@Email @NotBlank String email) {}
