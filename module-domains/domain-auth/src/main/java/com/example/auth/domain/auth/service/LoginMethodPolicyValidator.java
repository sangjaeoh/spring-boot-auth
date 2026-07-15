package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.PasswordCredentialRepository;
import com.example.auth.domain.auth.repository.SocialConnectionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 최소 1개 로그인 수단 유지 정책({@code LoginMethodPolicy})을 검증한다 — 비밀번호 자격증명과 소셜
 * 연동의 교차 애그리거트 read-check다.
 *
 * <p>동시 해제 경합의 최종 방어는 호출자가 잡는 {@code AuthAccount} 행 잠금이다 — 이 검증은 그 잠금
 * 아래(같은 트랜잭션)에서 호출돼야 직렬화가 성립한다.
 */
@Service
public class LoginMethodPolicyValidator {

    private final PasswordCredentialRepository passwordCredentialRepository;
    private final SocialConnectionRepository socialConnectionRepository;

    public LoginMethodPolicyValidator(
            PasswordCredentialRepository passwordCredentialRepository,
            SocialConnectionRepository socialConnectionRepository) {
        this.passwordCredentialRepository = passwordCredentialRepository;
        this.socialConnectionRepository = socialConnectionRepository;
    }

    /**
     * 소셜 연동 1건을 제거해도 로그인 수단이 남는지 검증한다.
     *
     * @throws AuthException 마지막 로그인 수단이면(409)
     */
    public void validateSocialDetachable(UUID userId) {
        if (passwordCredentialRepository.existsById(userId)) {
            return;
        }
        if (socialConnectionRepository.countByUserId(userId) <= 1) {
            throw new AuthException(AuthErrorCode.LAST_LOGIN_METHOD);
        }
    }
}
