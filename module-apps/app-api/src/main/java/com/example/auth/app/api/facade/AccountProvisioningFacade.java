package com.example.auth.app.api.facade;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.entity.RegistrationType;
import com.example.auth.domain.auth.info.RegistrationCompletionInfo;
import com.example.auth.domain.auth.service.AccountRegistrationProcessor;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.info.IdentityVerifiedInfo;
import com.example.auth.domain.user.port.IdentityProviderRequest;
import com.example.auth.domain.user.service.IdentityVerificationProcessor;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 로컬 인증 계정을 시드하는 부트스트랩/픽스처 경로다 — 온보딩 세션(Redis)만 생략하고 정식 가입 커밋
 * 경로(Mock 본인인증 → {@code CreateUser} 단일 크로스스키마 트랜잭션)를 그대로 태운다(파사드는
 * 트랜잭션을 열지 않는다).
 *
 * <p>본인인증 정체성은 {@code loginEmail}에서 결정적으로 파생한다 — Mock 기관이 정체성에서 CI를
 * 파생하므로 시드 간 CI가 유일해지고, 같은 이메일 재시드는 실 경로처럼 CI 중복으로 거부된다. 동의는
 * 시드 약관(V5) 현행 버전으로 제출해 완료 재검증 경로를 통과한다.
 */
@Component
public class AccountProvisioningFacade {

    private static final List<ConsentSelection> SEED_CONSENTS = List.of(
            new ConsentSelection(TermsType.SERVICE.name(), 1),
            new ConsentSelection(TermsType.PRIVACY_REQUIRED.name(), 1),
            new ConsentSelection(TermsType.AGE14.name(), 1));

    private final IdentityVerificationProcessor identityVerificationProcessor;
    private final AccountRegistrationProcessor accountRegistrationProcessor;

    public AccountProvisioningFacade(
            IdentityVerificationProcessor identityVerificationProcessor,
            AccountRegistrationProcessor accountRegistrationProcessor) {
        this.identityVerificationProcessor = identityVerificationProcessor;
        this.accountRegistrationProcessor = accountRegistrationProcessor;
    }

    /**
     * 정식 가입 커밋 경로로 회원·인증 계정·비밀번호 자격증명을 시드하고 생성된 {@code UserId}를 반환한다.
     */
    public UUID provision(String loginEmail, String rawPassword) {
        IdentityVerifiedInfo verified = identityVerificationProcessor.verify(new IdentityProviderRequest(
                "시드사용자", LocalDate.of(1990, 1, 1), Gender.MALE, Carrier.SKT, seedPhone(loginEmail)));
        RegistrationCompletionInfo completion = new RegistrationCompletionInfo(
                UuidV7Generator.generate(),
                RegistrationType.LOCAL,
                loginEmail,
                verified.verificationId(),
                verified.ciHash(),
                SEED_CONSENTS);
        return accountRegistrationProcessor.register(completion, rawPassword);
    }

    private static String seedPhone(String loginEmail) {
        return "+8210%08d".formatted(Math.floorMod(loginEmail.hashCode(), 100_000_000));
    }
}
