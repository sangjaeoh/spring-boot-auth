package com.example.auth.common.auth.jwt;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 검증된 Access 토큰이 운반하는 클레임이다.
 *
 * <p>{@code sessionId}는 매 요청 O(1) 세션검증의 키(`sid`), {@code userId}는 주체, {@code roles}는
 * 인가 집행용이다(불변 — 방어적 복사).
 */
public record AccessClaims(UUID userId, UUID sessionId, List<String> roles, Instant expiresAt) {

    public AccessClaims {
        roles = List.copyOf(roles);
    }
}
