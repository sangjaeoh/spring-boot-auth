package com.example.auth.common.web.security;

import java.util.UUID;

/**
 * 매 요청 세션 O(1) 유효성 검증의 웹 계층 포트다.
 *
 * <p>구현은 앱이 도메인 세션 스토어를 경유해 제공한다(common-web는 도메인에 의존하지 않는다). 무효·만료·
 * 폐기 세션은 {@code false}를 반환해 서명이 유효한 토큰도 거부되게 한다(실시간 무효화).
 */
public interface SessionValidationPort {

    /**
     * 주어진 사용자의 세션이 현재 활성(미폐기·미만료)인지 반환한다.
     */
    boolean isActive(UUID userId, UUID sessionId);
}
