package com.example.auth.app.api.facade;

import static java.util.Objects.requireNonNull;

import com.example.auth.app.api.presentation.v1.DeviceBindingRequest;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.common.auth.jwt.JwtIssuer;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.FailureReason;
import com.example.auth.domain.auth.entity.LoginResult;
import com.example.auth.domain.auth.event.LoggedIn;
import com.example.auth.domain.auth.event.LoginFailed;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.LoginAccountInfo;
import com.example.auth.domain.auth.info.RecognizedDeviceInfo;
import com.example.auth.domain.auth.info.RiskAssessmentInfo;
import com.example.auth.domain.auth.info.RotationOutcome;
import com.example.auth.domain.auth.info.SessionCreated;
import com.example.auth.domain.auth.service.AccountLockProcessor;
import com.example.auth.domain.auth.service.AuthAccountReader;
import com.example.auth.domain.auth.service.DeviceRecognitionService;
import com.example.auth.domain.auth.service.LoginAttemptAppender;
import com.example.auth.domain.auth.service.PasswordCredentialReader;
import com.example.auth.domain.auth.service.RateLimitPolicyValidator;
import com.example.auth.domain.auth.service.RiskEvaluator;
import com.example.auth.domain.auth.service.SessionProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로그인·로그아웃·토큰 재발급을 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지 않는다).
 *
 * <p>로그인 실패는 사유와 무관하게 단일 401 응답으로 통일하고, 계정 미존재 경로도 더미 검증을 태워 타이밍을
 * 맞춘다(열거 저항). 재사용 감지 시 세션 패밀리는 이미 무효화된 상태로 401을 반환한다.
 */
@Component
public class AuthFacade {

    private final AuthAccountReader authAccountReader;
    private final AccountLockProcessor accountLockProcessor;
    private final RateLimitPolicyValidator rateLimitPolicyValidator;
    private final PasswordCredentialReader passwordCredentialReader;
    private final LoginAttemptAppender loginAttemptAppender;
    private final DeviceRecognitionService deviceRecognitionService;
    private final RiskEvaluator riskEvaluator;
    private final SessionProcessor sessionProcessor;
    private final JwtIssuer jwtIssuer;
    private final MessagePublisher messagePublisher;
    private final Duration accessTtl;

    public AuthFacade(
            AuthAccountReader authAccountReader,
            AccountLockProcessor accountLockProcessor,
            RateLimitPolicyValidator rateLimitPolicyValidator,
            PasswordCredentialReader passwordCredentialReader,
            LoginAttemptAppender loginAttemptAppender,
            DeviceRecognitionService deviceRecognitionService,
            RiskEvaluator riskEvaluator,
            SessionProcessor sessionProcessor,
            JwtIssuer jwtIssuer,
            MessagePublisher messagePublisher,
            @Value("${auth.access.ttl-minutes:15}") int accessTtlMinutes) {
        this.authAccountReader = authAccountReader;
        this.accountLockProcessor = accountLockProcessor;
        this.rateLimitPolicyValidator = rateLimitPolicyValidator;
        this.passwordCredentialReader = passwordCredentialReader;
        this.loginAttemptAppender = loginAttemptAppender;
        this.deviceRecognitionService = deviceRecognitionService;
        this.riskEvaluator = riskEvaluator;
        this.sessionProcessor = sessionProcessor;
        this.jwtIssuer = jwtIssuer;
        this.messagePublisher = messagePublisher;
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    /**
     * 접근 판정·비밀번호 검증을 거쳐 기기를 인식하고(신규면 등록·감지 이벤트) 기기 바인딩 세션을 발급해
     * Access·Refresh 토큰을 반환한다.
     *
     * @throws AuthException 인증 실패 시(사유 미구분 401)
     */
    public TokenResponse login(
            String email, String rawPassword, DeviceBindingRequest device, String ip, @Nullable String userAgent) {
        Instant now = Instant.now();
        rateLimitPolicyValidator.checkLogin(ip, email);
        Optional<LoginAccountInfo> found = authAccountReader.findForLogin(email);
        if (found.isEmpty()) {
            passwordCredentialReader.verifyAbsent(rawPassword);
            fail(null, FailureReason.BAD_CREDENTIAL, ip, now);
        }
        LoginAccountInfo account = found.orElseThrow();
        // 쿨다운이 경과한 일시 잠금은 판정 직전에 해제한다(lazy — 스케줄러 없이 전이가 수렴).
        if (!account.loginAllowed()
                && account.blockReason() == FailureReason.LOCKED
                && accountLockProcessor.releaseIfCooldownElapsed(account.userId(), now)) {
            account = authAccountReader.findForLogin(email).orElseThrow();
        }
        if (!account.loginAllowed()) {
            if (account.dormant()) {
                // 휴면 안내는 자격 검증 성공자에게만 노출한다(열거 저항 — 실 KDF 1회로 타이밍 동일).
                if (passwordCredentialReader.verify(account.userId(), rawPassword)) {
                    loginAttemptAppender.record(
                            account.userId(), LoginResult.FAILURE, FailureReason.NOT_ACTIVE, ip, null, 0, null, now);
                    messagePublisher.publish(new LoginFailed(account.userId(), FailureReason.NOT_ACTIVE, now));
                    throw new AuthException(AuthErrorCode.ACCOUNT_DORMANT);
                }
                fail(account.userId(), FailureReason.BAD_CREDENTIAL, ip, now);
            }
            // 차단 계정도 KDF 1회를 태워 미존재·비번오류 경로와 응답 타이밍을 맞춘다(열거 저항).
            passwordCredentialReader.verifyAbsent(rawPassword);
            fail(account.userId(), requireNonNull(account.blockReason()), ip, now);
        }
        if (!passwordCredentialReader.verify(account.userId(), rawPassword)) {
            fail(account.userId(), FailureReason.BAD_CREDENTIAL, ip, now);
        }
        accountLockProcessor.resetFailures(account.userId());

        // 기기 인식은 자격 검증 성공 후에만 수행한다 — 실패 시도가 기기 행을 만들지 않게 한다.
        RecognizedDeviceInfo recognized = deviceRecognitionService.recognize(
                account.userId(), device.fingerprint(), device.deviceName(), device.platform(), ip, now);
        RiskAssessmentInfo risk = riskEvaluator.evaluate(account.userId(), recognized.newDevice(), ip, now);
        SessionCreated session =
                sessionProcessor.createSession(account.userId(), recognized.deviceId(), ip, userAgent, now);
        loginAttemptAppender.record(
                account.userId(),
                LoginResult.SUCCESS,
                null,
                ip,
                recognized.deviceId(),
                risk.riskScore(),
                risk.countryCode(),
                now);
        messagePublisher.publish(new LoggedIn(account.userId(), session.sessionId(), now));
        String accessToken =
                jwtIssuer.issueAccess(account.userId(), session.sessionId(), account.roles(), accessTtl, now);
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
                // 재발급이 역할 투영을 다시 읽는다 — RoleChanged 반영 지연의 상한이 Access TTL이 된다.
                List<String> roles = authAccountReader.getRoles(userId);
                String accessToken = jwtIssuer.issueAccess(userId, sessionId, roles, accessTtl, now);
                yield new TokenResponse(accessToken, requireNonNull(outcome.refreshToken()), accessTtl.toSeconds());
            }
            case REUSE_DETECTED -> throw new AuthException(AuthErrorCode.REFRESH_TOKEN_REUSE);
            case INVALID -> throw new AuthException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        };
    }

    private void fail(@Nullable UUID userId, FailureReason reason, String ip, Instant now) {
        loginAttemptAppender.record(userId, LoginResult.FAILURE, reason, ip, null, 0, null, now);
        if (userId != null && reason == FailureReason.BAD_CREDENTIAL) {
            // 연속 실패 잠금은 자격증명 실패만 센다 — 차단·비활성 시도는 자격 증거가 아니다.
            accountLockProcessor.recordFailure(userId, now);
        }
        messagePublisher.publish(new LoginFailed(userId, reason, now));
        throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED);
    }
}
