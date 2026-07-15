package com.example.auth.domain.auth.event;

import java.util.UUID;

/**
 * 로그인 상태에서 비밀번호가 변경된 도메인 이벤트다.
 *
 * <p>보안 카테고리 알림·감사의 트리거가 된다(소비는 후속 단계). 통합 이벤트로의 승격(아웃박스)은 물리
 * 분리 시 도입한다(IMPLEMENTATION_PLAN D3).
 */
public record PasswordChanged(UUID userId) {}
