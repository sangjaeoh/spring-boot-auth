package com.example.auth.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.entity.FailureReason;
import com.example.auth.domain.auth.entity.HashAlgorithm;
import com.example.auth.domain.auth.entity.LoginAttempt;
import com.example.auth.domain.auth.entity.LoginResult;
import com.example.auth.domain.auth.entity.PasswordCredential;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * auth 스키마 영속 E2E: Flyway 마이그레이션 → {@code ddl-auto=validate} → 저장/조회·유니크 강제.
 */
@SpringBootTest
@Import(JpaConfig.class)
@Testcontainers
class AuthPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Autowired
    private PasswordCredentialRepository passwordCredentialRepository;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Test
    void savesAndFindsAuthAccountByLoginEmail() {
        UUID userId = UUID.randomUUID();
        authAccountRepository.save(AuthAccount.create(userId, "User@Example.com"));

        Optional<AuthAccount> found = authAccountRepository.findByLoginEmail(Email.of("user@example.com"));

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().isLoginAllowed()).isTrue();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void enforcesLoginEmailUniqueness() {
        authAccountRepository.save(AuthAccount.create(UUID.randomUUID(), "dup@example.com"));

        assertThatThrownBy(() ->
                        authAccountRepository.saveAndFlush(AuthAccount.create(UUID.randomUUID(), "dup@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesPasswordCredential() {
        UUID userId = UUID.randomUUID();
        passwordCredentialRepository.save(
                PasswordCredential.create(userId, "$argon2id$hash", HashAlgorithm.ARGON2ID, Instant.now()));

        assertThat(passwordCredentialRepository.findById(userId)).isPresent();
    }

    @Test
    void appendsLoginAttempt() {
        LoginAttempt attempt = LoginAttempt.create(
                null, LoginResult.FAILURE, FailureReason.BAD_CREDENTIAL, "203.0.113.1", null, 0, null, Instant.now());

        loginAttemptRepository.save(attempt);

        assertThat(loginAttemptRepository.findById(attempt.getId())).isPresent();
    }
}
