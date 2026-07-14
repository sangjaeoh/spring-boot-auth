package com.example.auth.common.web.security;

import java.util.List;
import java.util.UUID;

/**
 * 인증된 요청의 주체다(SecurityContext 프린시펄).
 *
 * <p>Access 토큰에서 추출한 {@code userId}·{@code sessionId}·{@code roles}를 운반한다(불변).
 */
public record AuthUser(UUID userId, UUID sessionId, List<String> roles) {

    public AuthUser {
        roles = List.copyOf(roles);
    }
}
