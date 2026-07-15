package com.example.auth.domain.auth.event;

import com.example.auth.domain.auth.entity.SocialProvider;
import java.util.UUID;

/**
 * 소셜 로그인 수단이 해제된 도메인 이벤트다.
 */
public record SocialDisconnected(UUID userId, SocialProvider provider) {}
