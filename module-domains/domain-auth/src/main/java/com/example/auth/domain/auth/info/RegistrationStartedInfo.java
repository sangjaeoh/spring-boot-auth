package com.example.auth.domain.auth.info;

import java.util.UUID;

/**
 * 온보딩 시작 결과다. {@code onboardingToken}은 평문이 여기서 1회만 노출된다(저장은 해시) — 로깅 금지.
 */
public record RegistrationStartedInfo(
        UUID registrationId, String onboardingToken, String emailChallengeId, long expiresInSeconds) {}
