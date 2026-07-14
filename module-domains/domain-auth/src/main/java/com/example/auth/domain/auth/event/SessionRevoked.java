package com.example.auth.domain.auth.event;

import java.util.UUID;

/**
 * 세션 무효화 도메인 이벤트다(로그아웃·강제 종료).
 */
public record SessionRevoked(UUID userId, UUID sessionId) {}
