package com.example.auth.common.auth.exception;

import com.example.auth.common.core.exception.BaseException;

/**
 * Access 토큰 검증 실패 시 경계까지 전파되는 예외다(401 매핑).
 */
public class InvalidTokenException extends BaseException {

    public InvalidTokenException() {
        super(TokenErrorCode.INVALID_TOKEN);
    }
}
