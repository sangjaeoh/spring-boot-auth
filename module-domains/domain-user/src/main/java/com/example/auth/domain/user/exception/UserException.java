package com.example.auth.domain.user.exception;

import com.example.auth.common.core.exception.BaseException;
import com.example.auth.common.core.exception.ErrorCode;

/**
 * 유저 도메인의 경계 도달 예외다.
 */
public class UserException extends BaseException {

    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }
}
