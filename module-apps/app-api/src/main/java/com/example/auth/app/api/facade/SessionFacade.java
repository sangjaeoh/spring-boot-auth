package com.example.auth.app.api.facade;

import com.example.auth.app.api.presentation.v1.SessionResponse;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.ActiveSessionInfo;
import com.example.auth.domain.auth.info.DeviceInfo;
import com.example.auth.domain.auth.service.DeviceReader;
import com.example.auth.domain.auth.service.SessionProcessor;
import com.example.auth.domain.auth.service.SessionReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 내 세션 목록·원격 로그아웃을 조율한다(세션 진실원본은 Redis — 파사드는 트랜잭션을 열지 않는다).
 */
@Component
public class SessionFacade {

    private final SessionReader sessionReader;
    private final SessionProcessor sessionProcessor;
    private final DeviceReader deviceReader;

    public SessionFacade(SessionReader sessionReader, SessionProcessor sessionProcessor, DeviceReader deviceReader) {
        this.sessionReader = sessionReader;
        this.sessionProcessor = sessionProcessor;
        this.deviceReader = deviceReader;
    }

    /**
     * 내 활성 세션 목록을 최근 접속 순으로 반환한다(기기명·플랫폼 조인, 현재 세션 표시).
     */
    public List<SessionResponse> sessions(UUID userId, UUID currentSessionId) {
        Map<UUID, DeviceInfo> devicesById = deviceReader.findDevices(userId).stream()
                .collect(Collectors.toMap(DeviceInfo::deviceId, Function.identity()));
        return sessionReader.findActiveSessions(userId, Instant.now()).stream()
                .map(session -> toResponse(session, devicesById.get(session.deviceId()), currentSessionId))
                .toList();
    }

    /**
     * 특정 세션을 원격 종료한다(해당 세션의 토큰은 즉시 401).
     *
     * @throws AuthException 활성 세션이 아니면(404)
     */
    public void revoke(UUID userId, UUID sessionId) {
        if (!sessionProcessor.isActive(userId, sessionId, Instant.now())) {
            throw new AuthException(AuthErrorCode.SESSION_NOT_FOUND);
        }
        sessionProcessor.revoke(userId, sessionId);
    }

    /**
     * 현재 세션을 제외한 내 세션 전체를 종료한다.
     */
    public void revokeOthers(UUID userId, UUID currentSessionId) {
        sessionProcessor.revokeAllExcept(userId, currentSessionId);
    }

    private static SessionResponse toResponse(
            ActiveSessionInfo session, @Nullable DeviceInfo device, UUID currentSessionId) {
        return new SessionResponse(
                session.sessionId(),
                session.deviceId(),
                device == null ? null : device.deviceName(),
                device == null ? null : device.platform(),
                session.ip(),
                session.lastAccessedAt(),
                session.sessionId().equals(currentSessionId));
    }
}
