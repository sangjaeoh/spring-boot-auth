package com.example.auth.app.api.presentation.v1;

/**
 * 비밀번호 재설정 시작 응답 DTO다. 코드는 별도 채널로 전달되며 완료 요청에 {@code challengeId}를 함께 제출한다.
 */
public record PasswordResetInitiateResponse(String challengeId) {}
