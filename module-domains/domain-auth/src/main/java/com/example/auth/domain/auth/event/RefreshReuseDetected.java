package com.example.auth.domain.auth.event;

import java.util.UUID;

/**
 * 리프레시 토큰 재사용(탈취) 감지 도메인 이벤트다.
 *
 * <p>세션 패밀리는 이미 무효화되었으며, 보안 알림 발송의 트리거가 된다(소비는 후속 단계).
 */
public record RefreshReuseDetected(UUID userId, UUID sessionId) {}
