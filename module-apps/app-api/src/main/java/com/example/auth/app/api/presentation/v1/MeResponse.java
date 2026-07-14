package com.example.auth.app.api.presentation.v1;

import java.util.List;
import java.util.UUID;

/**
 * 현재 인증 주체 응답 DTO다.
 */
public record MeResponse(UUID userId, List<String> roles) {

    public MeResponse {
        roles = List.copyOf(roles);
    }
}
