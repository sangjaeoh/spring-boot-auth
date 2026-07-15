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
    PASSWORD_REUSED("AUTH_PASSWORD_REUSED", "최근 사용한 비밀번호는 다시 사용할 수 없습니다.", 400),
    CURRENT_PASSWORD_MISMATCH("AUTH_CURRENT_PASSWORD_MISMATCH", "현재 비밀번호가 올바르지 않습니다.", 400),
    PASSWORD_CREDENTIAL_NOT_FOUND("AUTH_PASSWORD_CREDENTIAL_NOT_FOUND", "비밀번호 자격증명이 없습니다.", 404),
    VERIFICATION_CODE_INVALID("AUTH_VERIFICATION_CODE_INVALID", "인증 코드가 올바르지 않거나 만료되었습니다.", 400),
    REGISTRATION_NOT_FOUND("AUTH_REGISTRATION_NOT_FOUND", "가입 세션이 존재하지 않거나 만료되었습니다.", 404),
    REGISTRATION_STEP_INCOMPLETE("AUTH_REGISTRATION_STEP_INCOMPLETE", "가입에 필요한 인증 절차가 완료되지 않았습니다.", 409),
    REFRESH_TOKEN_INVALID("AUTH_REFRESH_INVALID", "유효하지 않은 리프레시 토큰입니다.", 401),
    REFRESH_TOKEN_REUSE("AUTH_REFRESH_REUSE", "보안을 위해 세션이 종료되었습니다. 다시 로그인해 주세요.", 401),
    SOCIAL_TOKEN_INVALID("AUTH_SOCIAL_TOKEN_INVALID", "소셜 인증에 실패했습니다.", 401),
    SOCIAL_EMAIL_REQUIRED("AUTH_SOCIAL_EMAIL_REQUIRED", "소셜 계정에서 이메일을 확인할 수 없습니다. 이메일 제공에 동의해 주세요.", 400),
    SOCIAL_ALREADY_CONNECTED("AUTH_SOCIAL_ALREADY_CONNECTED", "이미 다른 계정에 연결된 소셜 계정입니다.", 409),
    SOCIAL_PROVIDER_ALREADY_LINKED("AUTH_SOCIAL_PROVIDER_ALREADY_LINKED", "이미 연동된 소셜 제공자입니다.", 409),
    SOCIAL_CONNECTION_NOT_FOUND("AUTH_SOCIAL_CONNECTION_NOT_FOUND", "연동된 소셜 계정이 없습니다.", 404),
    LAST_LOGIN_METHOD("AUTH_LAST_LOGIN_METHOD", "마지막 로그인 수단은 해제할 수 없습니다.", 409),
    SESSION_STORE_UNAVAILABLE("AUTH_SESSION_STORE_UNAVAILABLE", "일시적으로 세션 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.", 503);

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
