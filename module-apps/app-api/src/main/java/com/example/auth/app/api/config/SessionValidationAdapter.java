package com.example.auth.app.api.config;

import com.example.auth.common.web.security.SessionValidationPort;
import com.example.auth.domain.auth.service.SessionProcessor;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * common-web의 세션검증 포트를 도메인 세션 서비스에 연결하는 앱 어댑터다.
 *
 * <p>common-web은 도메인에 의존하지 않으므로, 매 요청 O(1) 세션검증의 실제 배선은 앱이 제공한다.
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
