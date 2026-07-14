package com.example.auth.common.core.exception;

/**
 * 도메인 에러 코드의 경계 계약이다.
 *
 * <p>코드 문자열·사용자 메시지·HTTP 상태를 노출한다. 도메인마다 {@code {Name}ErrorCode} enum이 이를
 * 구현하고, ProblemDetail 핸들러가 이 계약으로 응답을 만든다(docs/coding-conventions.md).
 */
public interface ErrorCode {

    /**
     * 안정 식별용 에러 코드 문자열을 반환한다.
     */
    String code();

    /**
     * 사용자에게 노출할 메시지를 반환한다.
     */
    String message();

    /**
     * 매핑되는 HTTP 상태 코드를 반환한다.
     */
    int status();
}
