package com.example.auth.domain.user.entity;

/**
 * CI 원장 항목 상태다.
 *
 * <p>ACTIVE_LINKED는 활성 회원 연결(신규 가입 차단), WITHDRAWN_RETAINED는 탈퇴 tombstone(부정재가입
 * 방지 유한 보존 후 파기 — P4)이다.
 */
public enum CiStatus {
    ACTIVE_LINKED,
    WITHDRAWN_RETAINED
}
