package com.example.auth.domain.auth.entity;

import java.util.Set;

/**
 * 가입 유형이다. 완료 조건은 고정 논리곱이 아니라 유형별 필수 스텝셋으로 판정한다(DOMAIN_MODEL §2.8).
 *
 * <p>SOCIAL은 이메일=IdP 검증·휴대폰=본인인증 결과로 충족되어 스텝이 줄어든다. SOCIAL 온보딩 경로
 * 자체는 P2에서 배선한다(스텝셋은 도메인 모델이 소유하는 데이터라 함께 선언).
 */
public enum RegistrationType {
    LOCAL,
    SOCIAL;

    /**
     * 이 가입 유형의 필수 스텝셋을 반환한다.
     */
    public Set<RegistrationStep> requiredSteps() {
        return switch (this) {
            case LOCAL ->
                Set.of(
                        RegistrationStep.EMAIL_VERIFIED,
                        RegistrationStep.PHONE_VERIFIED,
                        RegistrationStep.IDENTITY_VERIFIED,
                        RegistrationStep.REQUIRED_CONSENTED);
            case SOCIAL -> Set.of(RegistrationStep.IDENTITY_VERIFIED, RegistrationStep.REQUIRED_CONSENTED);
        };
    }

    /**
     * 완료된 스텝들이 이 유형의 필수 스텝셋을 전부 충족하는지 판정한다.
     */
    public boolean isSatisfiedBy(Set<RegistrationStep> completedSteps) {
        return completedSteps.containsAll(requiredSteps());
    }
}
