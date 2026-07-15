package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.user.info.ConsentStateInfo;
import com.example.auth.domain.user.info.RequiredConsentGapInfo;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 내 동의 현황 응답이다 — 현재 스냅샷 전체와 필수 약관 재동의 필요 목록.
 */
public record ConsentOverviewResponse(
        List<ConsentStateResponse> states, List<RequiredConsentResponse> pendingRequired) {

    public ConsentOverviewResponse {
        states = List.copyOf(states);
        pendingRequired = List.copyOf(pendingRequired);
    }

    /**
     * 도메인 경계 모델을 응답으로 변환한다.
     */
    public static ConsentOverviewResponse of(List<ConsentStateInfo> states, List<RequiredConsentGapInfo> gaps) {
        return new ConsentOverviewResponse(
                states.stream().map(ConsentStateResponse::from).toList(),
                gaps.stream().map(RequiredConsentResponse::from).toList());
    }

    /**
     * 현재 동의 스냅샷 한 건이다.
     */
    public record ConsentStateResponse(
            String termsType,
            String currentAction,
            @Nullable Integer agreedVersion,
            Instant updatedAt) {

        static ConsentStateResponse from(ConsentStateInfo info) {
            return new ConsentStateResponse(
                    info.termsType().name(), info.currentAction().name(), info.agreedVersion(), info.updatedAt());
        }
    }

    /**
     * 필수 약관 재동의 필요 항목이다.
     */
    public record RequiredConsentResponse(
            String termsType, int requiredVersion, @Nullable Integer agreedVersion) {

        static RequiredConsentResponse from(RequiredConsentGapInfo info) {
            return new RequiredConsentResponse(info.termsType().name(), info.requiredVersion(), info.agreedVersion());
        }
    }
}
