package com.example.auth.domain.auth.info;

import java.util.UUID;

/**
 * 세션 생성 결과다(경계 반환 — 리프레시 원문은 이 응답에서만 클라이언트에 전달된다).
 */
public record SessionCreated(UUID sessionId, UUID userId, String refreshToken) {}
