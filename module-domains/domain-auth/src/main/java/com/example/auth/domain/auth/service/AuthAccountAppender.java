package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 계정 생성을 담당한다.
 */
@Service
public class AuthAccountAppender {

    private final AuthAccountRepository repository;

    public AuthAccountAppender(AuthAccountRepository repository) {
        this.repository = repository;
    }

    /**
     * 유저 서비스가 채번한 {@code userId}로 인증 계정을 생성하고 그 ID를 반환한다.
     *
     * @throws AuthException 로그인 이메일이 이미 사용 중일 때
     */
    @Transactional
    public UUID register(UUID userId, String loginEmail) {
        Email email = Email.of(loginEmail);
        if (repository.existsByLoginEmail(email)) {
            throw new AuthException(AuthErrorCode.LOGIN_EMAIL_DUPLICATE);
        }
        repository.save(AuthAccount.create(userId, loginEmail));
        return userId;
    }
}
