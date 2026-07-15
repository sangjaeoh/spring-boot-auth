package com.example.auth.domain.user.info;

import com.example.auth.domain.user.entity.TermsType;
import org.jspecify.annotations.Nullable;

/**
 * 필수 약관 재동의 필요 항목이다 — 현행 필수 버전에 대한 동의가 없거나(신규 발행) 구버전 동의만 있는 경우.
 */
public record RequiredConsentGapInfo(
        TermsType termsType, int requiredVersion, @Nullable Integer agreedVersion) {}
