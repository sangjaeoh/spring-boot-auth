package com.example.auth.domain.user.entity;

/**
 * 성별이다(본인인증 결과). 암호화 대상이 아니라 enum 컬럼으로 저장한다(DOMAIN_MODEL 영속 유의절).
 */
public enum Gender {
    MALE,
    FEMALE
}
