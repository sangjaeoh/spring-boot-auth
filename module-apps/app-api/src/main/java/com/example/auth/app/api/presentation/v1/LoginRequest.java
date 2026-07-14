package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 DTO다.
 */
public record LoginRequest(
        @Email @NotBlank String email, @NotBlank String password) {}
