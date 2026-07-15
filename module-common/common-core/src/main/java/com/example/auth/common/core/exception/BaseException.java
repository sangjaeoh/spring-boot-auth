package com.example.auth.common.core.exception;

import java.util.Map;

/**
 * 경계에 도달하는 도메인 예외의 최상위 타입이다.
 *
 * <p>{@link ErrorCode} 하나를 받아 메시지·계층·HTTP 상태를 운반한다. 국소에서 잡아 처리하는 예외는
 * 이를 상속하지 않고 JDK 관용구를 쓴다(docs/coding-conventions.md). unchecked이므로
 * {@code @Transactional} 기본 롤백을 유발한다.
 */
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    /**
     * 이 예외가 운반하는 에러 코드를 반환한다.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 응답에 실을 추가 컨텍스트(예: 재가입 잔여일)를 반환한다. 기본은 비어 있고, 컨텍스트가 필요한
     * 예외만 재정의한다 — ProblemDetail 핸들러가 각 항목을 속성으로 노출한다.
     */
    public Map<String, Object> properties() {
        return Map.of();
    }
}
