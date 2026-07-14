package com.example.auth.domain.auth.entity;

/**
 * 회원 생명주기 상태의 인증-로컬 투영이다.
 *
 * <p>유저 서비스가 유일 writer이고 인증은 스냅샷으로만 읽는다. 값은 유저의 생명주기와 동일하되 타입은
 * 인증이 소유한다(크로스 seam 타입 공유 금지).
 */
public enum LifecycleStatus {
    ACTIVE,
    DORMANT,
    WITHDRAWN
}
