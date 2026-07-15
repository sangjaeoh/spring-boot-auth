package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 가입 완료 요청. 비밀번호 정책(길이·복잡도)은 도메인 {@code PasswordPolicyValidator}가 권위다.
 */
public record RegistrationCompleteRequest(
        @NotNull UUID registrationId,
        @NotBlank String onboardingToken,
        @NotBlank String password) {}
