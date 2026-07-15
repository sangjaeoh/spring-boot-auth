package com.example.auth.app.api.facade;

import static java.util.Objects.requireNonNull;

import com.example.auth.app.api.presentation.v1.DeviceBindingRequest;
import com.example.auth.app.api.presentation.v1.SocialLoginResponse;
import com.example.auth.app.api.presentation.v1.SocialRegistrationCompleteResponse;
import com.example.auth.app.api.presentation.v1.SocialRegistrationResponse;
import com.example.auth.app.api.presentation.v1.TokenResponse;
import com.example.auth.common.auth.jwt.JwtIssuer;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.FailureReason;
import com.example.auth.domain.auth.entity.LoginResult;
import com.example.auth.domain.auth.entity.SocialProvider;
import com.example.auth.domain.auth.event.LoggedIn;
import com.example.auth.domain.auth.event.LoginFailed;
import com.example.auth.domain.auth.event.SocialConnected;
import com.example.auth.domain.auth.event.SocialDisconnected;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.LoginAccountInfo;
import com.example.auth.domain.auth.info.RecognizedDeviceInfo;
import com.example.auth.domain.auth.info.RegistrationCompletionInfo;
import com.example.auth.domain.auth.info.SessionCreated;
import com.example.auth.domain.auth.info.SocialAuthenticationInfo;
import com.example.auth.domain.auth.info.SocialRegistrationStartedInfo;
import com.example.auth.domain.auth.service.AccountRegistrationProcessor;
import com.example.auth.domain.auth.service.AuthAccountReader;
import com.example.auth.domain.auth.service.DeviceRecognitionService;
import com.example.auth.domain.auth.service.LoginAttemptAppender;
import com.example.auth.domain.auth.service.RegistrationSessionProcessor;
import com.example.auth.domain.auth.service.SessionProcessor;
import com.example.auth.domain.auth.service.SocialConnectionProcessor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인·SOCIAL 온보딩 완료·연동/해제를 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는
 * 열지 않는다).
 *
 * <p>소셜 로그인 실패는 사유(토큰 위조·차단 계정)와 무관하게 단일 401로 통일한다(로컬 로그인과 동일한
 * 사유 비노출 원칙). 위조 토큰 시도는 계정 컨텍스트가 없어 로그인 이력에 남기지 않는다 — 이력은
 * 계정 단위 보안 기록이다.
 */
@Component
public class SocialAuthFacade {

    private static final List<String> DEFAULT_ROLES = List.of("USER");

    private final SocialConnectionProcessor socialConnectionProcessor;
    private final RegistrationSessionProcessor registrationSessionProcessor;
    private final AccountRegistrationProcessor accountRegistrationProcessor;
    private final AuthAccountReader authAccountReader;
    private final LoginAttemptAppender loginAttemptAppender;
    private final DeviceRecognitionService deviceRecognitionService;
    private final SessionProcessor sessionProcessor;
    private final JwtIssuer jwtIssuer;
    private final MessagePublisher messagePublisher;
    private final Duration accessTtl;

    public SocialAuthFacade(
            SocialConnectionProcessor socialConnectionProcessor,
            RegistrationSessionProcessor registrationSessionProcessor,
            AccountRegistrationProcessor accountRegistrationProcessor,
            AuthAccountReader authAccountReader,
            LoginAttemptAppender loginAttemptAppender,
            DeviceRecognitionService deviceRecognitionService,
            SessionProcessor sessionProcessor,
            JwtIssuer jwtIssuer,
            MessagePublisher messagePublisher,
            @Value("${auth.access.ttl-minutes:15}") int accessTtlMinutes) {
        this.socialConnectionProcessor = socialConnectionProcessor;
        this.registrationSessionProcessor = registrationSessionProcessor;
        this.accountRegistrationProcessor = accountRegistrationProcessor;
        this.authAccountReader = authAccountReader;
        this.loginAttemptAppender = loginAttemptAppender;
        this.deviceRecognitionService = deviceRecognitionService;
        this.sessionProcessor = sessionProcessor;
        this.jwtIssuer = jwtIssuer;
        this.messagePublisher = messagePublisher;
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
    }

    /**
     * {@code id_token}을 검증해 기존 연동 계정이면 접근 판정 후 세션을 발급하고, 미연동 신규면 SOCIAL
     * 온보딩 세션을 시작해 온보딩 토큰을 반환한다.
     *
     * @throws AuthException 토큰 검증 실패·차단 계정 시(사유 미구분 401), IdP가 이메일을 제공하지
     *     않으면(400)
     */
    public SocialLoginResponse login(
            SocialProvider provider,
            String idToken,
            DeviceBindingRequest device,
            String ip,
            @Nullable String userAgent) {
        Instant now = Instant.now();
        SocialAuthenticationInfo identity = socialConnectionProcessor.authenticate(provider, idToken);
        UUID connectedUserId = identity.connectedUserId();
        if (connectedUserId == null) {
            String email = identity.email();
            if (email == null) {
                throw new AuthException(AuthErrorCode.SOCIAL_EMAIL_REQUIRED);
            }
            SocialRegistrationStartedInfo started = registrationSessionProcessor.startSocial(
                    provider, identity.providerUserId(), email, identity.privateRelayEmail());
            return SocialLoginResponse.registrationRequired(SocialRegistrationResponse.from(started));
        }
        LoginAccountInfo account = authAccountReader
                .findForLogin(connectedUserId)
                .orElseThrow(() -> new IllegalStateException("연동이 가리키는 인증 계정이 없습니다: " + connectedUserId));
        if (!account.loginAllowed()) {
            fail(account.userId(), requireNonNull(account.blockReason()), ip, now);
        }
        return SocialLoginResponse.loggedIn(issueSession(account.userId(), device, ip, userAgent, now));
    }

    /**
     * SOCIAL 온보딩을 정회원으로 커밋한다 — 유저·인증 계정·소셜 연동을 단일 트랜잭션으로 원자 생성하고
     * (비밀번호 없는 계정), 성공 후 세션(멱등키)을 소비하고 로그인 세션을 발급한다.
     */
    public SocialRegistrationCompleteResponse completeRegistration(
            UUID registrationId,
            String onboardingToken,
            DeviceBindingRequest device,
            String ip,
            @Nullable String userAgent) {
        Instant now = Instant.now();
        RegistrationCompletionInfo completion = registrationSessionProcessor.complete(registrationId, onboardingToken);
        UUID userId = accountRegistrationProcessor.registerSocial(completion);
        registrationSessionProcessor.consume(registrationId);
        messagePublisher.publish(
                new SocialConnected(userId, requireNonNull(completion.social()).provider(), now));
        TokenResponse tokens = issueSession(userId, device, ip, userAgent, now);
        return new SocialRegistrationCompleteResponse(
                userId, tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds());
    }

    /**
     * 로그인 상태의 계정에 소셜 수단을 연동한다.
     */
    public void connect(UUID userId, SocialProvider provider, String idToken) {
        socialConnectionProcessor.connect(userId, provider, idToken);
        messagePublisher.publish(new SocialConnected(userId, provider, Instant.now()));
    }

    /**
     * 소셜 연동을 해제한다(최소 1개 로그인 수단 유지 — 동시 해제는 계정 행 잠금으로 직렬화).
     */
    public void disconnect(UUID userId, SocialProvider provider) {
        socialConnectionProcessor.disconnect(userId, provider);
        messagePublisher.publish(new SocialDisconnected(userId, provider, Instant.now()));
    }

    private TokenResponse issueSession(
            UUID userId, DeviceBindingRequest device, String ip, @Nullable String userAgent, Instant now) {
        RecognizedDeviceInfo recognized = deviceRecognitionService.recognize(
                userId, device.fingerprint(), device.deviceName(), device.platform(), ip, now);
        SessionCreated session = sessionProcessor.createSession(userId, recognized.deviceId(), ip, userAgent, now);
        loginAttemptAppender.record(userId, LoginResult.SUCCESS, null, ip, recognized.deviceId(), 0, now);
        messagePublisher.publish(new LoggedIn(userId, session.sessionId(), now));
        String accessToken = jwtIssuer.issueAccess(userId, session.sessionId(), DEFAULT_ROLES, accessTtl, now);
        return new TokenResponse(accessToken, session.refreshToken(), accessTtl.toSeconds());
    }

    private void fail(UUID userId, FailureReason reason, String ip, Instant now) {
        loginAttemptAppender.record(userId, LoginResult.FAILURE, reason, ip, null, 0, now);
        messagePublisher.publish(new LoginFailed(userId, reason, now));
        throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID);
    }
}
