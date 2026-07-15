package com.example.auth.app.api.facade;

import static java.util.Objects.requireNonNull;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.event.PasswordResetRequested;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.LoginAccountInfo;
import com.example.auth.domain.auth.port.NotificationChannel;
import com.example.auth.domain.auth.port.VerificationResult;
import com.example.auth.domain.auth.service.AuthAccountReader;
import com.example.auth.domain.auth.service.PasswordCredentialModifier;
import com.example.auth.domain.auth.service.PasswordPolicyValidator;
import com.example.auth.domain.auth.service.RateLimitPolicyValidator;
import com.example.auth.domain.auth.service.SessionProcessor;
import com.example.auth.domain.auth.service.VerificationChallengeProcessor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정·변경을 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지 않는다).
 *
 * <p>재설정 시작은 계정 존재 여부와 무관하게 {@code challengeId} 형태로 응답한다(열거 저항). 미존재 경로의
 * 발송 부재로 인한 미세한 응답 타이밍 차는 이 슬라이스에서 감수하며, P5 레이트리밋 도입 시 재검토한다.
 * 재설정 완료는 코드 소진 전 정책을 선검증해 회복 가능한 오류(정책 위반)로 일회용 코드를 낭비하지 않는다.
 * 코드 검증 이후 재사용 위반·재설정 실패 시엔 코드가 소진되므로 재시작이 필요하다.
 *
 * <p>재설정은 새 비밀번호 커밋 후 전 세션을 무효화한다. 무효화가 응답 반환 전에 실행되므로 성공 응답은 전
 * 세션 무효화를 보장한다(무효화 실패는 5xx로 드러난다 — 아웃박스 없이 성공 응답을 조용히 반환하는 이벤트
 * 방식보다 강한 보장). 커밋과 무효화 사이 프로세스 중단이라는 잔여 창은 D3 아웃박스가 물리 분리 시 닫는다.
 */
@Component
public class PasswordFacade {

    private final AuthAccountReader authAccountReader;
    private final VerificationChallengeProcessor challengeProcessor;
    private final PasswordCredentialModifier passwordCredentialModifier;
    private final PasswordPolicyValidator policyValidator;
    private final RateLimitPolicyValidator rateLimitPolicyValidator;
    private final SessionProcessor sessionProcessor;
    private final MessagePublisher messagePublisher;

    public PasswordFacade(
            AuthAccountReader authAccountReader,
            VerificationChallengeProcessor challengeProcessor,
            PasswordCredentialModifier passwordCredentialModifier,
            PasswordPolicyValidator policyValidator,
            RateLimitPolicyValidator rateLimitPolicyValidator,
            SessionProcessor sessionProcessor,
            MessagePublisher messagePublisher) {
        this.authAccountReader = authAccountReader;
        this.challengeProcessor = challengeProcessor;
        this.passwordCredentialModifier = passwordCredentialModifier;
        this.policyValidator = policyValidator;
        this.rateLimitPolicyValidator = rateLimitPolicyValidator;
        this.sessionProcessor = sessionProcessor;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 재설정을 시작해 인증코드를 발송하고 {@code challengeId}를 반환한다(미존재 계정도 동형 응답 —
     * 레이트리밋도 계정 존재와 무관하게 균일 적용한다).
     *
     * @throws AuthException 레이트리밋 초과 시(429)
     */
    public String initiateReset(String email, String ip) {
        rateLimitPolicyValidator.checkPasswordResetInitiate(ip, email);
        Optional<LoginAccountInfo> account = authAccountReader.findForLogin(email);
        if (account.isEmpty()) {
            return UuidV7Generator.generate().toString();
        }
        UUID userId = account.orElseThrow().userId();
        String challengeId = challengeProcessor.issue(userId, NotificationChannel.EMAIL, email);
        messagePublisher.publish(new PasswordResetRequested(userId, Instant.now()));
        return challengeId;
    }

    /**
     * 인증코드를 검증하고 새 비밀번호를 적용한 뒤 사용자의 전체 세션을 무효화한다.
     *
     * @throws AuthException 코드 무효; 정책 위반; 재사용; 자격증명 미존재
     */
    public void completeReset(String challengeId, String code, String newPassword) {
        // 코드 소진 전 정책 선검증(회복 가능한 오류로 일회용 코드를 낭비하지 않는다).
        policyValidator.validate(newPassword);
        VerificationResult result = challengeProcessor.verify(challengeId, code);
        if (!result.isVerified()) {
            throw new AuthException(AuthErrorCode.VERIFICATION_CODE_INVALID);
        }
        UUID userId = requireNonNull(result.subjectId());
        passwordCredentialModifier.resetTo(userId, newPassword, Instant.now());
        sessionProcessor.revokeAll(userId);
    }

    /**
     * 현재 비밀번호를 검증하고 새 비밀번호로 변경한다.
     *
     * <p>재설정과 달리 세션을 무효화하지 않는다 — 인증된 본인의 의도적 변경이라 현재 세션을 유지한다(다른
     * 기기 원격 로그아웃은 세션 관리 기능으로 P3에서 제공).
     *
     * @throws AuthException 현재 비밀번호 불일치; 정책 위반; 재사용; 자격증명 미존재
     */
    public void change(UUID userId, String currentPassword, String newPassword) {
        passwordCredentialModifier.change(userId, currentPassword, newPassword, Instant.now());
    }
}
