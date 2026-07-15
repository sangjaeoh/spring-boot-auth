package com.example.auth.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthAccountTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    private static final Duration COOLDOWN = Duration.ofMinutes(30);

    @Test
    void locksTemporarilyFromNone() {
        AuthAccount account = account();

        account.lockTemporarily(LockReason.CONSECUTIVE_LOGIN_FAILURE, NOW);

        assertThat(account.getLockState()).isEqualTo(LockState.TEMP_LOCKED);
        assertThat(account.getLockedAt()).isEqualTo(NOW);
        assertThat(account.getLockReason()).isEqualTo(LockReason.CONSECUTIVE_LOGIN_FAILURE);
        assertThat(account.isLoginAllowed()).isFalse();
    }

    @Test
    void rejectsLockingWhenAlreadyLocked() {
        AuthAccount account = account();
        account.lockTemporarily(LockReason.CONSECUTIVE_LOGIN_FAILURE, NOW);

        assertThatThrownBy(() -> account.lockTemporarily(LockReason.CONSECUTIVE_LOGIN_FAILURE, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releasesTemporaryLockAndClearsContext() {
        AuthAccount account = account();
        account.lockTemporarily(LockReason.CONSECUTIVE_LOGIN_FAILURE, NOW);

        account.releaseTemporaryLock();

        assertThat(account.getLockState()).isEqualTo(LockState.NONE);
        assertThat(account.getLockedAt()).isNull();
        assertThat(account.getLockReason()).isNull();
        assertThat(account.isLoginAllowed()).isTrue();
    }

    @Test
    void rejectsReleasingWhenNotTempLocked() {
        AuthAccount account = account();

        assertThatThrownBy(account::releaseTemporaryLock).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void temporaryLockExpiresAfterCooldown() {
        AuthAccount account = account();
        account.lockTemporarily(LockReason.CONSECUTIVE_LOGIN_FAILURE, NOW);

        assertThat(account.isTemporaryLockExpired(NOW.plus(COOLDOWN).minusSeconds(1), COOLDOWN))
                .isFalse();
        assertThat(account.isTemporaryLockExpired(NOW.plus(COOLDOWN), COOLDOWN)).isTrue();
    }

    @Test
    void unlockedAccountIsNeverExpired() {
        assertThat(account().isTemporaryLockExpired(NOW, COOLDOWN)).isFalse();
    }

    private AuthAccount account() {
        return AuthAccount.create(UUID.randomUUID(), "lock@example.com");
    }
}
