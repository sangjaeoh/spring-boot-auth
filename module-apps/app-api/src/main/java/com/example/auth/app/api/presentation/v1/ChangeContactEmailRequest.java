package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 연락용 이메일 변경 요청이다.
 */
public record ChangeContactEmailRequest(@NotBlank @Email String email) {}
