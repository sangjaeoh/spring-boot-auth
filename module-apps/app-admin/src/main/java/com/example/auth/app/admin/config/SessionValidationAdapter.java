package com.example.auth.app.admin.config;

import com.example.auth.common.web.security.SessionValidationPort;
import com.example.auth.domain.auth.service.SessionProcessor;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * common-web의 세션검증 포트를 도메인 세션 서비스에 연결하는 앱 어댑터다.
 *
 * <p>관리자 토큰도 매 요청 O(1) 세션검증을 통과해야 한다 — 강제 로그아웃·전멸이 관리자 세션에도 즉시
 * 반영된다(세션 저장소는 앱 간 공유 Redis).
 */
@Component
public class SessionValidationAdapter implements SessionValidationPort {

    private final SessionProcessor sessionProcessor;

    public SessionValidationAdapter(SessionProcessor sessionProcessor) {
        this.sessionProcessor = sessionProcessor;
    }

    @Override
    public boolean isActive(UUID userId, UUID sessionId) {
        return sessionProcessor.isActive(userId, sessionId, Instant.now());
    }
}
