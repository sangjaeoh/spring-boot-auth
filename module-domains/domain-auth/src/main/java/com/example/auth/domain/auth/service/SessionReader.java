package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.info.ActiveSessionInfo;
import com.example.auth.domain.auth.port.SessionStore;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 세션 조회를 담당한다(Redis 진실원본 — RDB 트랜잭션을 열지 않는다).
 */
@Service
public class SessionReader {

    private final SessionStore sessionStore;

    public SessionReader(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    /**
     * 사용자의 활성 세션 전체를 최근 접속 순으로 반환한다.
     */
    public List<ActiveSessionInfo> findActiveSessions(UUID userId, Instant now) {
        return sessionStore.findAllActive(userId, now).stream()
                .map(ActiveSessionInfo::from)
                .sorted(Comparator.comparing(ActiveSessionInfo::lastAccessedAt).reversed())
                .toList();
    }
}
