package com.example.auth.app.api.presentation.v1;

import java.util.UUID;

/**
 * 가입 완료 응답. 생성(재실행이면 기존)된 회원의 {@code UserId}다.
 */
public record RegistrationCompleteResponse(UUID userId) {}
