package com.example.auth.domain.auth.port;

import com.example.auth.domain.auth.entity.SocialProvider;

/**
 * SOCIAL 온보딩 세션이 보관하는 검증된 소셜 신원 컨텍스트다(가입 완료 시 {@code SocialConnection.connect}
 * 재료). IdP 이메일은 세션의 {@code loginEmail}로 보관하므로 여기 중복 저장하지 않는다.
 */
public record SocialRegistrationContext(SocialProvider provider, String providerUserId, boolean privateRelayEmail) {}
