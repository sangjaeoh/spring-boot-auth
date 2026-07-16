package com.example.auth.app.admin.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.domain.auth.entity.LockState;
import com.example.auth.domain.user.entity.LifecycleStatus;
import org.junit.jupiter.api.Test;

/**
 * 실효 상태 합성 우선순위(WITHDRAWN &gt; LOCKED &gt; DORMANT &gt; ACTIVE)의 조합 렌더를 검증한다.
 */
class EffectiveMemberStatusTest {

    @Test
    void withdrawnWinsOverAnyLock() {
        assertThat(EffectiveMemberStatus.of(LifecycleStatus.WITHDRAWN, LockState.NONE))
                .isEqualTo(EffectiveMemberStatus.WITHDRAWN);
        assertThat(EffectiveMemberStatus.of(LifecycleStatus.WITHDRAWN, LockState.ADMIN_LOCKED))
                .isEqualTo(EffectiveMemberStatus.WITHDRAWN);
    }

    @Test
    void lockWinsOverDormancy() {
        assertThat(EffectiveMemberStatus.of(LifecycleStatus.DORMANT, LockState.TEMP_LOCKED))
                .isEqualTo(EffectiveMemberStatus.LOCKED);
        assertThat(EffectiveMemberStatus.of(LifecycleStatus.ACTIVE, LockState.ADMIN_LOCKED))
                .isEqualTo(EffectiveMemberStatus.LOCKED);
    }

    @Test
    void dormancyWinsOverActive() {
        assertThat(EffectiveMemberStatus.of(LifecycleStatus.DORMANT, LockState.NONE))
                .isEqualTo(EffectiveMemberStatus.DORMANT);
    }

    @Test
    void activeWhenNoOverlay() {
        assertThat(EffectiveMemberStatus.of(LifecycleStatus.ACTIVE, LockState.NONE))
                .isEqualTo(EffectiveMemberStatus.ACTIVE);
    }
}
