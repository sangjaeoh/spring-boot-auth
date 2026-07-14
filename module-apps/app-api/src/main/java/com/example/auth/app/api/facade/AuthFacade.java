package com.example.auth.app.api.facade;

import static java.util.Objects.requireNonNull;

import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.common.auth.jwt.JwtIssuer;
import com.example.auth.domain.auth.entity.FailureReason;
import com.example.auth.domain.auth.entity.LoginResult;
import com.example.auth.domain.auth.event.LoggedIn;
import com.example.auth.domain.auth.event.LoginFailed;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.LoginAccountInfo;
import com.example.auth.domain.auth.info.RotationOutcome;
import com.example.auth.domain.auth.info.SessionCreated;
import com.example.auth.domain.auth.service.AuthAccountReader;
import com.example.auth.domain.auth.service.LoginAttemptAppender;
import com.example.auth.domain.auth.service.PasswordCredentialReader;
import com.example.auth.domain.auth.service.SessionProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 로그인·로그아웃·토큰 재발급을 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지 않는다).
 *
 * <p>로그인 실패는 사유와 무관하게 단일 401 응답으로 통일하고, 계정 미존재 경로도 더미 검증을 태워 타이밍을
 * 맞춘다(열거 저항). 재사용 감지 시 세션 패밀리는 이미 무효화된 상태로 401을 반환한다.
 */
@Component
public class AuthFacade {

    private static final List<String> DEFAULT_ROLES = List.of("USER");

    private final AuthAccountReader authAccountReader;
    private final PasswordCredentialReader passwordCredentialReader;
    private final LoginAttemptAppender loginAttemptAppender;
    private final SessionProcessor sessionProcessor;
    private final JwtIssuer jwtIssuer;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration accessTtl;

    public AuthFacade(
            AuthAccountReader authAccountReader,
            PasswordCredentialReader passwordCredentialReader,
            LoginAttemptAppender loginAttemptAppender,
            SessionProcessor sessionProcessor,
            JwtIssuer jwtIssuer,
            ApplicationEventPublisher eventPublisher,
            @Value("${auth.access.ttl-minutes:15}") int accessTtlMinutes) {
        this.authAccountReader = authAccountReader;
        this.passwordCredentialReader = passwordCredentialReader;
        this.loginAttemptAppender = loginAttemptAppender;
        this.sessionProcessor = sessionProcessor;
        this.jwtIssuer = jwtIssuer;
        this.eventPublisher = eventPublisher;
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    /**
     * 접근 판정·비밀번호 검증을 거쳐 세션을 발급하고 Access·Refresh 토큰을 반환한다.
     *
     * @throws AuthException 인증 실패 시(사유 미구분 401)
     */
    public TokenResponse login(String email, String rawPassword, String ip, @Nullable String userAgent) {
        Instant now = Instant.now();
        Optional<LoginAccountInfo> found = authAccountReader.findForLogin(email);
        if (found.isEmpty()) {
            passwordCredentialReader.verifyAbsent(rawPassword);
            fail(null, FailureReason.BAD_CREDENTIAL, ip, now);
        }
        LoginAccountInfo account = found.orElseThrow();
        if (!account.loginAllowed()) {
            // 차단 계정도 KDF 1회를 태워 미존재·비번오류 경로와 응답 타이밍을 맞춘다(열거 저항).
            passwordCredentialReader.verifyAbsent(rawPassword);
            fail(account.userId(), requireNonNull(account.blockReason()), ip, now);
        }
        if (!passwordCredentialReader.verify(account.userId(), rawPassword)) {
            fail(account.userId(), FailureReason.BAD_CREDENTIAL, ip, now);
        }

        SessionCreated session = sessionProcessor.createSession(account.userId(), null, ip, userAgent, now);
        loginAttemptAppender.record(account.userId(), LoginResult.SUCCESS, null, ip, null, 0, now);
        eventPublisher.publishEvent(new LoggedIn(account.userId(), session.sessionId(), now));
        String accessToken =
                jwtIssuer.issueAccess(account.userId(), session.sessionId(), DEFAULT_ROLES, accessTtl, now);
        return new TokenResponse(accessToken, session.refreshToken(), accessTtl.toSeconds());
    }

    /**
     * 현재 세션을 무효화한다(로그아웃 후 기존 Access는 즉시 401).
     */
    public void logout(UUID userId, UUID sessionId) {
        sessionProcessor.revoke(userId, sessionId);
    }

    /**
     * 리프레시를 회전해 새 토큰을 발급한다. 재사용·무효는 401로 응답한다.
     *
     * @throws AuthException 재사용 감지·무효 토큰 시
     */
    public TokenResponse refresh(String presentedRefresh) {
        Instant now = Instant.now();
        RotationOutcome outcome = sessionProcessor.rotate(presentedRefresh, now);
        return switch (outcome.outcome()) {
            case ROTATED -> {
                UUID userId = requireNonNull(outcome.userId());
                UUID sessionId = requireNonNull(outcome.sessionId());
                String accessToken = jwtIssuer.issueAccess(userId, sessionId, DEFAULT_ROLES, accessTtl, now);
                yield new TokenResponse(accessToken, requireNonNull(outcome.refreshToken()), accessTtl.toSeconds());
            }
            case REUSE_DETECTED -> throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSE);
            case INVALID -> throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        };
    }

    private void fail(@Nullable UUID userId, FailureReason reason, String ip, Instant now) {
        loginAttemptAppender.record(userId, LoginResult.FAILURE, reason, ip, null, 0, now);
        eventPublisher.publishEvent(new LoginFailed(userId, reason, now));
        throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED);
    }
}
