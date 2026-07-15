package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 휴면 해제 OTP 발송 요청이다(휴면 계정의 자격 재검증).
 */
public record DormantReleaseChallengeRequest(
        @NotBlank @Email String email, @NotBlank String password) {}
