package com.example.auth.app.api.facade;

import com.example.auth.domain.auth.service.AuthAccountAppender;
import com.example.auth.domain.auth.service.PasswordCredentialAppender;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 로컬 인증 계정을 시드하는 부트스트랩/픽스처 경로다(P1b 온보딩이 재사용할 실제 appender 경로).
 *
 * <p>인증 계정과 비밀번호 자격증명을 각자 트랜잭션으로 생성한다(파사드는 트랜잭션을 열지 않는다). 원자적
 * 정회원 생성은 P1b의 {@code CreateUser} 단일 트랜잭션이 소유한다 — 이 경로는 시드 전용이다.
 */
@Component
public class AccountProvisioningFacade {

    private final AuthAccountAppender authAccountAppender;
    private final PasswordCredentialAppender passwordCredentialAppender;

    public AccountProvisioningFacade(
            AuthAccountAppender authAccountAppender, PasswordCredentialAppender passwordCredentialAppender) {
        this.authAccountAppender = authAccountAppender;
        this.passwordCredentialAppender = passwordCredentialAppender;
    }

    /**
     * 주어진 {@code userId}로 인증 계정과 비밀번호 자격증명을 시드하고 그 ID를 반환한다.
     */
    public UUID provision(UUID userId, String loginEmail, String rawPassword) {
        authAccountAppender.register(userId, loginEmail);
        passwordCredentialAppender.register(userId, rawPassword, Instant.now());
        return userId;
    }
}
