package com.example.auth.app.api.facade;

import static java.util.Objects.requireNonNull;

import com.example.auth.app.api.presentation.v1.DormantReleaseChallengeResponse;
import com.example.auth.common.core.masking.PiiMasker;
import com.example.auth.domain.auth.entity.LifecycleStatus;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.LoginAccountInfo;
import com.example.auth.domain.auth.port.NotificationChannel;
import com.example.auth.domain.auth.port.VerificationResult;
import com.example.auth.domain.auth.service.AuthAccountModifier;
import com.example.auth.domain.auth.service.AuthAccountReader;
import com.example.auth.domain.auth.service.PasswordCredentialReader;
import com.example.auth.domain.auth.service.VerificationChallengeProcessor;
import com.example.auth.domain.user.service.DormancyProcessor;
import com.example.auth.domain.user.service.UserReader;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 휴면 해제(OTP 재인증) 플로우를 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지 않는다).
 *
 * <p>휴면 사실은 자격 검증 성공자에게만 노출한다(열거 저항 — 그 외는 단일 401). 해제 권위는 유저다:
 * OTP 검증 성공 후에도 {@code DormancyProcessor.reactivate}가 유저 권위 상태(DORMANT)를 재검증하므로
 * 인증 스냅샷이 어떻게 보이든 OTP 없는 해제 경로는 없다. 해제 반영은 이벤트 소비(내구)와 동기 반영
 * (즉시 로그인 가능)을 겸한다 — 단조 버전 멱등이라 중복 무해.
 */
@Component
public class DormantAccountFacade {

    private final AuthAccountReader authAccountReader;
    private final PasswordCredentialReader passwordCredentialReader;
    private final VerificationChallengeProcessor verificationChallengeProcessor;
    private final UserReader userReader;
    private final DormancyProcessor dormancyProcessor;
    private final AuthAccountModifier authAccountModifier;

    public DormantAccountFacade(
            AuthAccountReader authAccountReader,
            PasswordCredentialReader passwordCredentialReader,
            VerificationChallengeProcessor verificationChallengeProcessor,
            UserReader userReader,
            DormancyProcessor dormancyProcessor,
            AuthAccountModifier authAccountModifier) {
        this.authAccountReader = authAccountReader;
        this.passwordCredentialReader = passwordCredentialReader;
        this.verificationChallengeProcessor = verificationChallengeProcessor;
        this.userReader = userReader;
        this.dormancyProcessor = dormancyProcessor;
        this.authAccountModifier = authAccountModifier;
    }

    /**
     * 자격 검증(이메일+비밀번호) 후 연락용 이메일로 해제 OTP를 발송하고 챌린지ID·마스킹 수신처를 반환한다.
     *
     * @throws AuthException 미존재·비휴면·자격 불일치 시(단일 401 — 열거 저항)
     */
    public DormantReleaseChallengeResponse startRelease(String email, String rawPassword) {
        Optional<LoginAccountInfo> found = authAccountReader.findForLogin(email);
        if (found.isEmpty() || !found.orElseThrow().dormant()) {
            passwordCredentialReader.verifyAbsent(rawPassword);
            throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        UUID userId = found.orElseThrow().userId();
        if (!passwordCredentialReader.verify(userId, rawPassword)) {
            throw new AuthException(AuthErrorCode.AUTHENTICATION_FAILED);
        }
        String contactEmail = userReader.getContactEmail(userId);
        String challengeId = verificationChallengeProcessor.issue(userId, NotificationChannel.EMAIL, contactEmail);
        return new DormantReleaseChallengeResponse(challengeId, PiiMasker.maskEmail(contactEmail));
    }

    /**
     * OTP를 검증하고 휴면을 해제한다(유저 권위 재검증 후 인증 스냅샷 동기 반영 — 직후 정상 로그인 가능).
     *
     * @throws AuthException 코드 불일치·만료·시도 초과 시(400)
     */
    public void completeRelease(String challengeId, String code) {
        VerificationResult result = verificationChallengeProcessor.verify(challengeId, code);
        if (result.status() != VerificationResult.Status.VERIFIED) {
            throw new AuthException(AuthErrorCode.VERIFICATION_CODE_INVALID);
        }
        UUID userId = requireNonNull(result.subjectId());
        long statusVersion = dormancyProcessor.reactivate(userId, Instant.now());
        authAccountModifier.applyUserStatus(userId, LifecycleStatus.ACTIVE, statusVersion);
    }
}
