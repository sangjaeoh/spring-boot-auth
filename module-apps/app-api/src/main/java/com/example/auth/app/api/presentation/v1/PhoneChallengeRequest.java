package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record PhoneChallengeRequest(
        @NotNull UUID registrationId,
        @NotBlank String onboardingToken,

        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "E.164 형식이어야 합니다")
        String phone) {}
