package com.example.auth.domain.user.info;

import com.example.auth.domain.user.entity.ConsentAction;
import com.example.auth.domain.user.entity.ConsentState;
import com.example.auth.domain.user.entity.TermsType;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * 현재 동의 스냅샷 경계 조회 모델이다.
 */
public record ConsentStateInfo(
        TermsType termsType,
        ConsentAction currentAction,
        @Nullable Integer agreedVersion,
        Instant updatedAt) {

    /**
     * 스냅샷 엔티티를 경계 모델로 변환한다.
     */
    public static ConsentStateInfo from(ConsentState state) {
        return new ConsentStateInfo(
                state.getTermsType(), state.getCurrentAction(), state.getAgreedVersion(), state.getUpdatedAt());
    }
}
