package com.example.auth.domain.auth.exception;

import com.example.auth.common.core.exception.ErrorCode;

/**
 * 인증 도메인 에러 코드다.
 *
 * <p>로그인 실패는 사유(미존재·비번오류·비활성·잠금)와 무관하게 단일 {@code AUTHENTICATION_FAILED}로
 * 응답해 계정 열거를 막는다(IMPLEMENTATION_PLAN §5).
 */
public enum AuthErrorCode implements ErrorCode {
    LOGIN_EMAIL_DUPLICATE("AUTH_LOGIN_EMAIL_DUPLICATE", "이미 사용 중인 이메일입니다.", 409),
    AUTHENTICATION_FAILED("AUTH_AUTHENTICATION_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다.", 401),
    PASSWORD_POLICY_VIOLATION("AUTH_PASSWORD_POLICY", "비밀번호가 정책을 충족하지 않습니다.", 400),
    REFRESH_TOKEN_INVALID("AUTH_REFRESH_INVALID", "유효하지 않은 리프레시 토큰입니다.", 401),
    REFRESH_TOKEN_REUSE("AUTH_REFRESH_REUSE", "보안을 위해 세션이 종료되었습니다. 다시 로그인해 주세요.", 401);

    private final String code;
    private final String message;
    private final int status;

    AuthErrorCode(String code, String message, int status) {
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
