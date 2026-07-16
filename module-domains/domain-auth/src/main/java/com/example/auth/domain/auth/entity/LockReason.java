package com.example.auth.domain.auth.entity;

/**
 * 계정 잠금 사유다({@code lockState} 전이의 컨텍스트).
 */
public enum LockReason {
    CONSECUTIVE_LOGIN_FAILURE,
    ADMIN_ACTION
}
