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
    TERMS_VERSION_INVALID("USER_TERMS_VERSION_INVALID", "약관 버전이 유효하지 않습니다.", 400),
    TERMS_TYPE_NOT_FOUND("USER_TERMS_TYPE_NOT_FOUND", "약관 유형이 존재하지 않습니다.", 404),
    REQUIRED_CONSENT_WITHDRAWAL("USER_REQUIRED_CONSENT_WITHDRAWAL", "필수 약관은 철회할 수 없습니다. 탈퇴 절차를 이용해주세요.", 400),
    CONSENT_NOT_FOUND("USER_CONSENT_NOT_FOUND", "철회할 동의가 존재하지 않습니다.", 404),
    SECURITY_CHANNEL_REQUIRED("USER_SECURITY_CHANNEL_REQUIRED", "보안 알림은 이메일·SMS 중 최소 한 채널을 유지해야 합니다.", 400),
    USER_NOT_FOUND("USER_NOT_FOUND", "회원이 존재하지 않습니다.", 404),
    USER_ALREADY_WITHDRAWN("USER_ALREADY_WITHDRAWN", "이미 탈퇴한 회원입니다.", 409),
    INVALID_STATUS_TRANSITION("USER_INVALID_STATUS_TRANSITION", "회원 상태 전이가 올바르지 않습니다.", 409),
    REJOIN_COOLDOWN("USER_REJOIN_COOLDOWN", "탈퇴 후 재가입 제한 기간입니다.", 409);

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
