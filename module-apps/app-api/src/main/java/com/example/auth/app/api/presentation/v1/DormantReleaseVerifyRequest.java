package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotBlank;

/**
 * 휴면 해제 OTP 검증 요청이다.
 */
public record DormantReleaseVerifyRequest(
        @NotBlank String challengeId, @NotBlank String code) {}
