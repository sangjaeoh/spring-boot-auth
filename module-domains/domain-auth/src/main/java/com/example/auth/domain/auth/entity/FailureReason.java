package com.example.auth.domain.auth.entity;

/**
 * 로그인 실패 사유다(이력 기록용 — 클라이언트 응답은 열거 저항을 위해 사유를 구분하지 않는다).
 */
public enum FailureReason {
    BAD_CREDENTIAL,
    NOT_ACTIVE,
    LOCKED
}
