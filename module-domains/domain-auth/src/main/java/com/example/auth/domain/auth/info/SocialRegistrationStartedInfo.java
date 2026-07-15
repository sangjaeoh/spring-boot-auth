package com.example.auth.domain.auth.info;

import java.util.UUID;

/**
 * SOCIAL 온보딩 시작 결과다. {@code onboardingToken}은 평문이 여기서 1회만 노출된다(저장은 해시) —
 * 로깅 금지. LOCAL과 달리 이메일 챌린지가 없다(이메일 소유는 IdP가 검증).
 */
public record SocialRegistrationStartedInfo(UUID registrationId, String onboardingToken, long expiresInSeconds) {}
