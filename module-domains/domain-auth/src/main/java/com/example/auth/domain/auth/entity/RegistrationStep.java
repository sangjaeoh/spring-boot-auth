package com.example.auth.domain.auth.entity;

/**
 * 온보딩(RegistrationSession) 스텝이다. 가입유형별 필수 스텝셋은 {@link RegistrationType}이 소유한다.
 */
public enum RegistrationStep {
    EMAIL_VERIFIED,
    PHONE_VERIFIED,
    IDENTITY_VERIFIED,
    REQUIRED_CONSENTED
}
