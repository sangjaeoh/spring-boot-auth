package com.example.auth.domain.user.entity;

/**
 * 본인인증 진행 상태다. REQUESTED가 최초이며 VERIFIED·FAILED·EXPIRED는 종료 상태다.
 *
 * <p>EXPIRED 전이(진행 만료 스윕)는 P4 배치에서 배선한다 — 그 전까지 포트 런타임 예외로 남은 REQUESTED
 * 행은 {@code expiresAt} 기준으로 그 스윕이 수렴시킨다.
 */
public enum VerificationStatus {
    REQUESTED,
    VERIFIED,
    FAILED,
    EXPIRED
}
