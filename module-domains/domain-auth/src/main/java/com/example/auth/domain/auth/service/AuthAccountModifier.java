package com.example.auth.domain.auth.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.event.LoginEmailChanged;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 계정 단일 애그리거트 전이를 담당한다.
 */
@Service
public class AuthAccountModifier {

    private final AuthAccountRepository repository;
    private final MessagePublisher messagePublisher;

    public AuthAccountModifier(AuthAccountRepository repository, MessagePublisher messagePublisher) {
        this.repository = repository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 로그인 식별자를 변경한다(정규화 후 유니크 — 연락용 이메일과 독립). 변경 사실을 보안 이벤트로 발행한다.
     *
     * @throws AuthException 미존재 계정이면(404), 이미 사용 중인 이메일이면(409)
     */
    @Transactional
    public void changeLoginEmail(UUID userId, String newEmail) {
        Email email = Email.of(newEmail);
        AuthAccount account =
                repository.findById(userId).orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_NOT_FOUND));
        if (email.equals(account.getLoginEmail())) {
            return;
        }
        if (repository.existsByLoginEmail(email)) {
            throw new AuthException(AuthErrorCode.LOGIN_EMAIL_DUPLICATE);
        }
        account.changeLoginEmail(email);
        messagePublisher.publish(new LoginEmailChanged(userId, Instant.now()));
    }
}
