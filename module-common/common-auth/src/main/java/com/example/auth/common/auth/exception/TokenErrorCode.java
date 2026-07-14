package com.example.auth.common.auth.exception;

import com.example.auth.common.core.exception.ErrorCode;

/**
 * 토큰 검증 계층의 에러 코드다.
 *
 * <p>만료·위변조를 구분하지 않고 단일 {@code INVALID_TOKEN}으로 노출해 열거 공격 표면을 줄인다
 * (IMPLEMENTATION_PLAN §5 — enumeration 저항).
 */
public enum TokenErrorCode implements ErrorCode {
    INVALID_TOKEN("AUTH_INVALID_TOKEN", "유효하지 않은 인증 토큰입니다.", 401);

    private final String code;
    private final String message;
    private final int status;

    TokenErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int status() {
        return status;
    }
}
