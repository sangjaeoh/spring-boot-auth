package com.example.auth.app.api.presentation.v1;

/**
 * 휴면 해제 OTP 발송 응답이다(수신처는 마스킹 표시).
 */
public record DormantReleaseChallengeResponse(String challengeId, String maskedTarget) {}
