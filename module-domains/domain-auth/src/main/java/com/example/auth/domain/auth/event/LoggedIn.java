package com.example.auth.domain.auth.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 로그인 성공 도메인 이벤트다.
 */
public record LoggedIn(UUID userId, UUID sessionId, Instant at) {}
