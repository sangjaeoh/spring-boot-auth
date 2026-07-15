package com.example.auth.domain.user.entity;

/**
 * 약관 유형이다(DOMAIN_MODEL §1.4). 유형별 필수 여부는 {@code TermsDocument.required}가 소유한다.
 */
public enum TermsType {
    SERVICE,
    PRIVACY_REQUIRED,
    PRIVACY_OPTIONAL,
    AGE14,
    MARKETING,
    THIRD_PARTY
}
