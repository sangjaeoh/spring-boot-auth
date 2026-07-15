package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EmailChallengeRequest(
        @NotNull UUID registrationId, @NotBlank String onboardingToken) {}
