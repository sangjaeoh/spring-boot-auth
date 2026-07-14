package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 비밀번호 정책(길이·복잡도)을 강제한다.
 */
@Service
public class PasswordPolicyValidator {

    private final int minLength;

    public PasswordPolicyValidator(@Value("${auth.password.min-length:8}") int minLength) {
        this.minLength = minLength;
    }

    /**
     * 정책을 위반하면 예외를 던진다: 최소 길이 이상 ∧ 문자·숫자를 각 1개 이상 포함.
     *
     * @throws AuthException 정책 위반 시
     */
    public void validate(String rawPassword) {
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (rawPassword.length() < minLength || !hasLetter || !hasDigit) {
            throw new AuthException(AuthErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }
}
