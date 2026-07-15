package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.entity.RegistrationType;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.RegistrationCompletionInfo;
import com.example.auth.domain.auth.port.SocialRegistrationContext;
import com.example.auth.domain.auth.port.UserCreationRequest;
import com.example.auth.domain.auth.port.UserRegistrar;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 완료 번들을 정회원으로 커밋한다 — 유저({@code CreateUser}: User·ConsentRecord·CiRegistry.link·
 * IdentityVerification 연결)와 인증({@code AuthAccount} + 가입 유형별 로그인 수단: LOCAL은
 * {@code PasswordCredential}, SOCIAL은 {@code SocialConnection})을 <b>한 ACID 트랜잭션</b>에서 원자
 * 생성한다. 중간 실패는 두 스키마 전량 롤백된다(고아-User 불가).
 *
 * <p>"한 트랜잭션 하나의 애그리거트" 원칙과 이벤트 최종일관성 규칙의 문서화된 예외다
 * (docs/architecture.md 트랜잭션 경계 — 단일 PostgreSQL + 스키마 분리 토폴로지 전제). 유저 쓰기는
 * {@link UserRegistrar} 포트로 진입해 이 트랜잭션에 참여한다(도메인 상호 비의존 유지).
 *
 * <p>멱등하다: 재실행이면 {@code CreateUser}가 기존 {@code UserId}를 재반환하고, 그 계정은 원자 커밋
 * 보장으로 이미 존재하므로 인증 쓰기를 건너뛴다(재실행에 제출된 비밀번호는 무시된다).
 */
@Service
public class AccountRegistrationProcessor {

    private final UserRegistrar userRegistrar;
    private final AuthAccountRepository authAccountRepository;
    private final AuthAccountAppender authAccountAppender;
    private final PasswordCredentialAppender passwordCredentialAppender;
    private final SocialConnectionAppender socialConnectionAppender;

    public AccountRegistrationProcessor(
            UserRegistrar userRegistrar,
            AuthAccountRepository authAccountRepository,
            AuthAccountAppender authAccountAppender,
            PasswordCredentialAppender passwordCredentialAppender,
            SocialConnectionAppender socialConnectionAppender) {
        this.userRegistrar = userRegistrar;
        this.authAccountRepository = authAccountRepository;
        this.authAccountAppender = authAccountAppender;
        this.passwordCredentialAppender = passwordCredentialAppender;
        this.socialConnectionAppender = socialConnectionAppender;
    }

    /**
     * LOCAL 가입: 정회원과 인증 계정·비밀번호 자격증명을 원자 생성하고 {@code UserId}를 반환한다
     * (재실행 시 기존 {@code UserId} 재반환).
     *
     * @throws AuthException SOCIAL 세션 번들이 제출되면(404 — 표면 교차 차단), 로그인 이메일이 이미
     *     사용 중이면(409), 비밀번호가 정책을 위반하면(400)
     */
    @Transactional
    public UUID register(RegistrationCompletionInfo completion, String rawPassword) {
        // SOCIAL 세션을 LOCAL 완료 표면으로 커밋하면 소셜 연동 없이 비밀번호 계정이 생긴다 — 차단.
        if (completion.type() != RegistrationType.LOCAL) {
            throw new AuthException(AuthErrorCode.REGISTRATION_NOT_FOUND);
        }
        UUID userId = createUser(completion);
        if (authAccountRepository.existsById(userId)) {
            return userId;
        }
        authAccountAppender.register(userId, completion.loginEmail());
        passwordCredentialAppender.register(userId, rawPassword, Instant.now());
        return userId;
    }

    /**
     * SOCIAL 가입: 정회원과 인증 계정·소셜 연동을 원자 생성하고 {@code UserId}를 반환한다(재실행 시
     * 기존 {@code UserId} 재반환). 비밀번호 없는 계정이다({@code PasswordCredential} 0..1).
     *
     * @throws AuthException LOCAL 세션 번들이 제출되면(404 — 표면 교차 차단), 로그인 이메일이 이미
     *     사용 중이면(409), 소셜 계정이 이미 타 계정에 연결됐으면(409)
     */
    @Transactional
    public UUID registerSocial(RegistrationCompletionInfo completion) {
        SocialRegistrationContext social = completion.social();
        if (completion.type() != RegistrationType.SOCIAL || social == null) {
            throw new AuthException(AuthErrorCode.REGISTRATION_NOT_FOUND);
        }
        UUID userId = createUser(completion);
        if (authAccountRepository.existsById(userId)) {
            return userId;
        }
        authAccountAppender.register(userId, completion.loginEmail());
        socialConnectionAppender.connect(
                userId,
                social.provider(),
                social.providerUserId(),
                completion.loginEmail(),
                social.privateRelayEmail(),
                Instant.now());
        return userId;
    }

    private UUID createUser(RegistrationCompletionInfo completion) {
        return userRegistrar.createUser(new UserCreationRequest(
                completion.verificationRef(), completion.ciHash(), completion.loginEmail(), completion.consents()));
    }
}
