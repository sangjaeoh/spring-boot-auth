package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.entity.SocialConnection;
import com.example.auth.domain.auth.entity.SocialProvider;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.SocialAuthenticationInfo;
import com.example.auth.domain.auth.port.SocialIdentityOutcome;
import com.example.auth.domain.auth.port.SocialIdentityProvider;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import com.example.auth.domain.auth.repository.SocialConnectionRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 인증·연동·해제 플로우를 조율한다.
 *
 * <p>{@code id_token} 검증(실 어댑터는 외부 HTTP 왕복)은 DB 트랜잭션 밖에서 수행한다 — 검증이 필요한
 * 메서드는 트랜잭션을 열지 않고, 쓰기는 별도 트랜잭션 소유 서비스({@link SocialConnectionAppender})나
 * 자체 {@code @Transactional} 메서드(해제)로 분리한다.
 */
@Service
public class SocialConnectionProcessor {

    private static final Logger log = LoggerFactory.getLogger(SocialConnectionProcessor.class);

    private final SocialIdentityProvider socialIdentityProvider;
    private final SocialConnectionRepository socialConnectionRepository;
    private final AuthAccountRepository authAccountRepository;
    private final SocialConnectionAppender socialConnectionAppender;
    private final LoginMethodPolicyValidator loginMethodPolicyValidator;

    public SocialConnectionProcessor(
            SocialIdentityProvider socialIdentityProvider,
            SocialConnectionRepository socialConnectionRepository,
            AuthAccountRepository authAccountRepository,
            SocialConnectionAppender socialConnectionAppender,
            LoginMethodPolicyValidator loginMethodPolicyValidator) {
        this.socialIdentityProvider = socialIdentityProvider;
        this.socialConnectionRepository = socialConnectionRepository;
        this.authAccountRepository = authAccountRepository;
        this.socialConnectionAppender = socialConnectionAppender;
        this.loginMethodPolicyValidator = loginMethodPolicyValidator;
    }

    /**
     * {@code id_token}을 검증하고 검증된 신원과 기존 연동 여부를 반환한다.
     *
     * @throws AuthException 토큰 검증 실패 시(401 — 사유 미노출)
     */
    public SocialAuthenticationInfo authenticate(SocialProvider provider, String idToken) {
        SocialIdentityOutcome.Verified verified = requireVerified(provider, idToken);
        UUID connectedUserId = socialConnectionRepository
                .findByProviderAndProviderUserId(provider, verified.subject())
                .map(SocialConnection::getUserId)
                .orElse(null);
        return new SocialAuthenticationInfo(
                verified.subject(), verified.email(), verified.privateRelayEmail(), connectedUserId);
    }

    /**
     * {@code id_token}을 검증하고 로그인 상태의 계정에 소셜 수단을 연동한다.
     *
     * @throws AuthException 토큰 검증 실패 시(401), 유니크 불변식 위반 시(409)
     */
    public void connect(UUID userId, SocialProvider provider, String idToken) {
        SocialIdentityOutcome.Verified verified = requireVerified(provider, idToken);
        socialConnectionAppender.connect(
                userId, provider, verified.subject(), verified.email(), verified.privateRelayEmail(), Instant.now());
    }

    /**
     * 소셜 연동을 해제한다. {@code AuthAccount} 행 잠금 아래에서 최소 1개 로그인 수단 유지를 재검증해
     * 동시 해제를 직렬화한다(둘 다 "하나 남음"을 관측해 0이 되는 경합 차단).
     *
     * @throws AuthException 연동이 없으면(404), 마지막 로그인 수단이면(409)
     */
    @Transactional
    public void disconnect(UUID userId, SocialProvider provider) {
        // 인증된 주체의 계정 행이 없는 것은 외부 입력이 아니라 데이터 정합 훼손이다.
        if (authAccountRepository.findWithLockByUserId(userId).isEmpty()) {
            throw new IllegalStateException("인증 계정이 존재하지 않습니다: " + userId);
        }
        SocialConnection connection = socialConnectionRepository
                .findByUserIdAndProvider(userId, provider)
                .orElseThrow(() -> new AuthException(AuthErrorCode.SOCIAL_CONNECTION_NOT_FOUND));
        loginMethodPolicyValidator.validateSocialDetachable(userId);
        socialConnectionRepository.delete(connection);
    }

    private SocialIdentityOutcome.Verified requireVerified(SocialProvider provider, String idToken) {
        SocialIdentityOutcome outcome = socialIdentityProvider.verify(provider, idToken);
        if (outcome instanceof SocialIdentityOutcome.Failed failed) {
            // 실패 사유는 로그로만 남긴다(클라이언트는 단일 401 — 검증 내부를 노출하지 않는다).
            log.info("소셜 id_token 검증 실패 provider={} reason={}", provider, failed.reason());
            throw new AuthException(AuthErrorCode.SOCIAL_TOKEN_INVALID);
        }
        return (SocialIdentityOutcome.Verified) outcome;
    }
}
