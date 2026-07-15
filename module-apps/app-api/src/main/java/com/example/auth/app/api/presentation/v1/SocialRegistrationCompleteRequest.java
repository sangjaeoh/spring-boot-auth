package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * SOCIAL 가입 완료 요청. 비밀번호가 없다 — 로그인 수단은 온보딩 세션이 보관한 소셜 연동이다.
 */
public record SocialRegistrationCompleteRequest(
        @NotNull UUID registrationId, @NotBlank String onboardingToken) {}
