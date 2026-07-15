package com.example.auth.domain.user.entity;

/**
 * 본인확인기관이다(실명확인 제공자).
 *
 * <p>{@code MOCK}은 dev/test 전용 어댑터의 정직한 표기다 — 실기관 값을 사칭하지 않고 데이터에 남긴다.
 * 실 기관 어댑터는 P4에서 배선한다.
 */
public enum Provider {
    NICE,
    KG,
    DANAL,
    MOCK
}
