package com.example.auth.domain.auth.entity;

/**
 * 인증 계정의 보안 잠금 오버레이 상태다(생명주기와 직교).
 */
public enum LockState {
    NONE,
    TEMP_LOCKED,
    ADMIN_LOCKED
}
