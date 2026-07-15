package com.example.auth.domain.auth.info;

import org.jspecify.annotations.Nullable;

/**
 * 로그인 위험도 평가 결과다 — 이력 기록({@code LoginAttempt.riskScore·countryCode})에 쓰는 값.
 */
public record RiskAssessmentInfo(int riskScore, @Nullable String countryCode) {}
