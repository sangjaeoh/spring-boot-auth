package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.info.LoginAccountInfo;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 계정 조회를 담당한다.
 */
@Service
public class AuthAccountReader {

    private final AuthAccountRepository repository;

    public AuthAccountReader(AuthAccountRepository repository) {
        this.repository = repository;
    }

    /**
     * 로그인 식별자로 접근 판정 스냅샷을 조회한다.
     */
    @Transactional(readOnly = true)
    public Optional<LoginAccountInfo> findForLogin(String loginEmail) {
        return repository.findByLoginEmail(Email.of(loginEmail)).map(LoginAccountInfo::from);
    }
}
