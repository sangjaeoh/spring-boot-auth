package com.example.auth.domain.auth.exception;

import com.example.auth.common.core.exception.BaseException;

/**
 * 인증 도메인의 경계 도달 예외다.
 */
public class AuthException extends BaseException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }
}
