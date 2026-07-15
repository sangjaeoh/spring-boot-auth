package com.example.auth.domain.user.info;

import java.util.UUID;

/**
 * 본인인증 성공 결과의 경계 반환값이다 — 온보딩이 세션에 보관할 참조({@code verificationId})와 중복
 * 판정 키({@code ciHash})만 내보낸다(PII 원문은 유저 경계 안에 가둔다).
 */
public record IdentityVerifiedInfo(UUID verificationId, String ciHash) {}
