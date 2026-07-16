package com.example.auth.app.admin.infrastructure.query;

import com.example.auth.domain.auth.entity.LockState;
import com.example.auth.domain.user.entity.LifecycleStatus;

/**
 * 실효 회원상태 읽기모델이다 — 생명주기(유저)와 잠금(인증) 두 축을 우선순위
 * {@code WITHDRAWN > LOCKED > DORMANT > ACTIVE}로 합성한다(두 축을 한 컬럼에 병합하지 않는다).
 */
public enum EffectiveMemberStatus {
    ACTIVE,
    DORMANT,
    LOCKED,
    WITHDRAWN;

    /**
     * 생명주기와 잠금 상태를 합성해 실효 상태를 반환한다.
     */
    public static EffectiveMemberStatus of(LifecycleStatus lifecycle, LockState lockState) {
        if (lifecycle == LifecycleStatus.WITHDRAWN) {
            return WITHDRAWN;
        }
        if (lockState != LockState.NONE) {
            return LOCKED;
        }
        if (lifecycle == LifecycleStatus.DORMANT) {
            return DORMANT;
        }
        return ACTIVE;
    }
}
