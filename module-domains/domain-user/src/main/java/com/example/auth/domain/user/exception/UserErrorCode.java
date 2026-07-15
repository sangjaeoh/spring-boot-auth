package com.example.auth.domain.user.exception;

import com.example.auth.common.core.exception.ErrorCode;

/**
 * 유저 도메인 에러 코드다.
 */
public enum UserErrorCode implements ErrorCode {
    IDENTITY_VERIFICATION_FAILED("USER_IDENTITY_VERIFICATION_FAILED", "본인인증에 실패했습니다.", 400),
    VERIFICATION_STATE_INVALID("USER_VERIFICATION_STATE_INVALID", "본인인증 상태 전이가 올바르지 않습니다.", 409),
    DUPLICATE_CI("USER_DUPLICATE_CI", "이미 가입된 회원입니다.", 409),
    REQUIRED_CONSENT_MISSING("USER_REQUIRED_CONSENT_MISSING", "필수 약관 동의가 누락되었습니다.", 400),
    TERMS_VERSION_INVALID("USER_TERMS_VERSION_INVALID", "약관 버전이 유효하지 않습니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    UserErrorCode(String code, String message, int status) {
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
