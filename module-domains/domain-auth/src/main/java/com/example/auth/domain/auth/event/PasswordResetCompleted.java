package com.example.auth.domain.auth.event;

import java.util.UUID;

/**
 * 비밀번호 재설정이 완료된 도메인 이벤트다(새 비밀번호 적용·전 세션 무효화 이후).
 *
 * <p>보안 알림·감사의 트리거가 된다(소비는 후속 단계).
 */
public record PasswordResetCompleted(UUID userId) {}
