package com.example.auth.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 가입유형별 필수 스텝셋 충족 판정을 검증한다(DOMAIN_MODEL §2.8 — 고정 논리곱이 아니라 유형별 스텝셋).
 */
class RegistrationTypeTest {

    @Test
    void localRequiresAllFourSteps() {
        Set<RegistrationStep> allButPhone = EnumSet.of(
                RegistrationStep.EMAIL_VERIFIED,
                RegistrationStep.IDENTITY_VERIFIED,
                RegistrationStep.REQUIRED_CONSENTED);

        assertThat(RegistrationType.LOCAL.isSatisfiedBy(allButPhone)).isFalse();
        assertThat(RegistrationType.LOCAL.isSatisfiedBy(EnumSet.allOf(RegistrationStep.class)))
                .isTrue();
    }

    @Test
    void socialRequiresOnlyIdentityAndConsent() {
        Set<RegistrationStep> identityAndConsent =
                EnumSet.of(RegistrationStep.IDENTITY_VERIFIED, RegistrationStep.REQUIRED_CONSENTED);

        assertThat(RegistrationType.SOCIAL.isSatisfiedBy(identityAndConsent)).isTrue();
        assertThat(RegistrationType.SOCIAL.isSatisfiedBy(EnumSet.of(RegistrationStep.IDENTITY_VERIFIED)))
                .isFalse();
    }

    @Test
    void emptyStepsNeverSatisfy() {
        assertThat(RegistrationType.LOCAL.isSatisfiedBy(EnumSet.noneOf(RegistrationStep.class)))
                .isFalse();
        assertThat(RegistrationType.SOCIAL.isSatisfiedBy(EnumSet.noneOf(RegistrationStep.class)))
                .isFalse();
    }
}
