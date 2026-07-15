package com.example.auth.common.web.handler;

import com.example.auth.common.core.exception.BaseException;
import com.example.auth.common.core.exception.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 경계에 도달한 {@link BaseException}을 {@link ProblemDetail}로 변환한다.
 *
 * <p>{@link ErrorCode}의 상태·메시지·코드로 응답을 만든다(docs/coding-conventions.md — ProblemDetail 계약).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 도메인 예외를 상태·코드·메시지를 담은 ProblemDetail로 변환한다. 예외가 추가 컨텍스트를 노출하면
     * ({@code properties()}) 각 항목을 속성으로 싣는다.
     */
    @ExceptionHandler(BaseException.class)
    public ProblemDetail handleBaseException(BaseException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(errorCode.status()), errorCode.message());
        problemDetail.setProperty("code", errorCode.code());
        exception.properties().forEach(problemDetail::setProperty);
        return problemDetail;
    }
}
