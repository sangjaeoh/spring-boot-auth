package com.example.auth.domain.auth.info;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * {@code id_token} 검증을 통과한 소셜 신원과 기존 연동 여부다(경계 조회 모델).
 *
 * <p>{@code connectedUserId}가 있으면 기존 연동 계정(즉시 로그인 경로), 없으면 미연동 신규(SOCIAL
 * 온보딩 유도 경로)다.
 */
public record SocialAuthenticationInfo(
        String providerUserId,
        @Nullable String email,
        boolean privateRelayEmail,
        @Nullable UUID connectedUserId) {}
