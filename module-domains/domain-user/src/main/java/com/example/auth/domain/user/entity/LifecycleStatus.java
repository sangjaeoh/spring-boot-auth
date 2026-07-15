package com.example.auth.domain.user.entity;

/**
 * 회원 생명주기 상태다(유저 서비스가 소유하는 캐노니컬 축).
 *
 * <p>인증 서비스의 동명 스냅샷 타입과는 별개다(크로스 seam 타입 공유 금지). 최초 상태는 온보딩 사전조건
 * 충족 후 ACTIVE다. DORMANT·WITHDRAWN 전이는 P4(휴면·탈퇴)에서 writer가 등장할 때 배선한다.
 */
public enum LifecycleStatus {
    ACTIVE,
    DORMANT,
    WITHDRAWN
}
