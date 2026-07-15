package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 이메일 변경 요청이다.
 */
public record ChangeLoginEmailRequest(@NotBlank @Email String email) {}
