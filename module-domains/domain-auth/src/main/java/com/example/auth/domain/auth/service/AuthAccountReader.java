package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.LoginAccountInfo;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import java.util.Optional;
import java.util.UUID;
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

    /**
     * 계정 식별자로 접근 판정 스냅샷을 조회한다(소셜 로그인 — 기존 연동의 {@code userId} 경로).
     */
    @Transactional(readOnly = true)
    public Optional<LoginAccountInfo> findForLogin(UUID userId) {
        return repository.findById(userId).map(LoginAccountInfo::from);
    }

    /**
     * 계정의 로그인 이메일 원문을 반환한다(내 정보 조회용 — 표시 마스킹은 호출측 정책).
     *
     * @throws AuthException 미존재 계정이면(404)
     */
    @Transactional(readOnly = true)
    public String getLoginEmail(UUID userId) {
        // 탈퇴 계정은 loginEmail이 파기(null)돼 미존재와 동일하게 취급한다.
        return repository
                .findById(userId)
                .map(AuthAccount::getLoginEmail)
                .map(Email::value)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_NOT_FOUND));
    }
}
