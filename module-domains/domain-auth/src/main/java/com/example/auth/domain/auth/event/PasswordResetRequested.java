package com.example.auth.domain.auth.event;

import java.util.UUID;

/**
 * 비밀번호 재설정이 요청된 도메인 이벤트다(인증코드 발급 시점).
 *
 * <p>보안 알림·감사의 트리거가 된다(소비는 후속 단계).
 */
public record PasswordResetRequested(UUID userId) {}
