package com.example.auth.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.domain.auth.exception.AuthException;
import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator(8);

    @Test
    void acceptsPolicyCompliantPassword() {
        assertThatCode(() -> validator.validate("secret123")).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShort() {
        assertThatThrownBy(() -> validator.validate("ab12")).isInstanceOf(AuthException.class);
    }

    @Test
    void rejectsMissingDigit() {
        assertThatThrownBy(() -> validator.validate("onlyletters")).isInstanceOf(AuthException.class);
    }

    @Test
    void rejectsMissingLetter() {
        assertThatThrownBy(() -> validator.validate("12345678")).isInstanceOf(AuthException.class);
    }
}
