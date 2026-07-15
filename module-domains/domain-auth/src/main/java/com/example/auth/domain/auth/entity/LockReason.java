package com.example.auth.domain.auth.entity;

/**
 * 계정 잠금 사유다({@code lockState} 전이의 컨텍스트).
 *
 * <p>관리자 잠금 사유는 관리자 콘솔 슬라이스(ADMIN_LOCKED 전이)가 추가한다.
 */
public enum LockReason {
    CONSECUTIVE_LOGIN_FAILURE
}
