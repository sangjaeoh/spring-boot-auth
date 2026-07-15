package com.example.auth.domain.auth.service;

import static java.util.Objects.requireNonNull;

import com.example.auth.common.core.crypto.TokenHasher;
import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.event.RefreshReuseDetected;
import com.example.auth.domain.auth.event.SessionRevoked;
import com.example.auth.domain.auth.info.RotationOutcome;
import com.example.auth.domain.auth.info.SessionCreated;
import com.example.auth.domain.auth.port.RotationResult;
import com.example.auth.domain.auth.port.SessionStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 세션(AccountSessions) 애그리거트의 생성·회전·무효화를 조율한다.
 *
 * <p>원자성은 {@link SessionStore}(Lua)가 보장하므로 RDB 트랜잭션을 열지 않는다. 리프레시 원문은 불투명
 * 고엔트로피 랜덤이며 저장소엔 해시만 넘긴다. 재사용 감지 시 패밀리 무효화는 저장소가 원자 수행하고 여기선
 * 보안 이벤트만 발행한다.
 */
@Service
public class SessionProcessor {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final SessionStore sessionStore;
    private final TokenHasher tokenHasher;
    private final MessagePublisher messagePublisher;
    private final SecureRandom random = new SecureRandom();
    private final Duration sessionTtl;
    private final Duration graceWindow;

    public SessionProcessor(
            SessionStore sessionStore,
            TokenHasher tokenHasher,
            MessagePublisher messagePublisher,
            @Value("${auth.session.ttl-days:14}") int sessionTtlDays,
            @Value("${auth.refresh.grace-seconds:10}") long graceSeconds) {
        this.sessionStore = sessionStore;
        this.tokenHasher = tokenHasher;
        this.messagePublisher = messagePublisher;
        this.sessionTtl = Duration.ofDays(sessionTtlDays);
        this.graceWindow = Duration.ofSeconds(graceSeconds);
    }

    /**
     * 새 세션을 생성하고 세션ID·리프레시 원문을 반환한다.
     */
    public SessionCreated createSession(
            UUID userId, @Nullable UUID deviceId, String ip, @Nullable String userAgent, Instant now) {
        UUID sessionId = UuidV7Generator.generate();
        String refreshPlain = newOpaqueToken();
        String jtiHash = tokenHasher.hash(refreshPlain);
        sessionStore.create(userId, sessionId, deviceId, ip, userAgent, jtiHash, refreshPlain, now, sessionTtl);
        return new SessionCreated(sessionId, userId, refreshPlain);
    }

    /**
     * 제시된 리프레시를 회전한다. 유예 재시도는 동일 토큰을 멱등 재반환하고, 유예 밖 재사용은 세션 패밀리를
     * 무효화하며 {@link RefreshReuseDetected}를 발행한다.
     */
    public RotationOutcome rotate(String presentedRefresh, Instant now) {
        String presentedJtiHash = tokenHasher.hash(presentedRefresh);
        String newRefreshPlain = newOpaqueToken();
        String newJtiHash = tokenHasher.hash(newRefreshPlain);
        RotationResult result =
                sessionStore.rotate(presentedJtiHash, newJtiHash, newRefreshPlain, now, graceWindow, sessionTtl);
        return switch (result.type()) {
            case ROTATED, GRACE_REPLAY ->
                RotationOutcome.rotated(
                        requireNonNull(result.userId()),
                        requireNonNull(result.sessionId()),
                        requireNonNull(result.refreshPlain()));
            case REUSE -> {
                UUID userId = requireNonNull(result.userId());
                UUID sessionId = requireNonNull(result.sessionId());
                messagePublisher.publish(new RefreshReuseDetected(userId, sessionId, now));
                yield RotationOutcome.reuseDetected();
            }
            case INVALID -> RotationOutcome.invalid();
        };
    }

    /**
     * 세션이 현재 활성인지 O(1)로 검증한다(매 요청 핫패스).
     */
    public boolean isActive(UUID userId, UUID sessionId, Instant now) {
        return sessionStore.validate(userId, sessionId, now);
    }

    /**
     * 단일 세션을 무효화한다(로그아웃).
     */
    public void revoke(UUID userId, UUID sessionId) {
        sessionStore.revoke(userId, sessionId);
        messagePublisher.publish(new SessionRevoked(userId, sessionId, Instant.now()));
    }

    /**
     * 사용자의 전체 세션을 무효화한다(비밀번호 재설정 등 자격증명 변동 시 기존 세션 전멸).
     */
    public void revokeAll(UUID userId) {
        sessionStore.revokeAll(userId);
    }

    private String newOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
