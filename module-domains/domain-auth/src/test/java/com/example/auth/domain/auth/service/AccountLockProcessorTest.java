package com.example.auth.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.LockReason;
import com.example.auth.domain.auth.entity.LockState;
import com.example.auth.domain.auth.event.AccountLocked;
import com.example.auth.domain.auth.event.AccountUnlocked;
import com.example.auth.domain.auth.port.LoginFailureCounter;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountLockProcessorTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    private AuthAccountRepository repository;
    private LoginFailureCounter counter;
    private MessagePublisher messagePublisher;
    private AccountLockProcessor processor;

    @BeforeEach
    void setUp() {
        repository = mock(AuthAccountRepository.class);
        counter = mock(LoginFailureCounter.class);
        messagePublisher = mock(MessagePublisher.class);
        processor = new AccountLockProcessor(repository, counter, messagePublisher, 5, 1800);
    }

    @Test
    void staysUnlockedBelowThreshold() {
        UUID userId = UUID.randomUUID();
        when(counter.increment(eq(userId), any())).thenReturn(4L);

        assertThat(processor.recordFailure(userId, NOW)).isFalse();
        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void locksAtThresholdAndPublishesAccountLocked() {
        UUID userId = UUID.randomUUID();
        AuthAccount account = AuthAccount.create(userId, "lock@example.com");
        when(counter.increment(eq(userId), any())).thenReturn(5L);
        when(repository.findWithLockByUserId(userId)).thenReturn(Optional.of(account));

        assertThat(processor.recordFailure(userId, NOW)).isTrue();

        assertThat(account.getLockState()).isEqualTo(LockState.TEMP_LOCKED);
        verify(counter).reset(userId);
        verify(messagePublisher).publish(any(AccountLocked.class));
    }

    @Test
    void absorbsAlreadyLockedAccountWithoutRepublishing() {
        UUID userId = UUID.randomUUID();
        AuthAccount account = AuthAccount.create(userId, "lock@example.com");
        account.lockTemporarily(LockReason.CONSECUTIVE_LOGIN_FAILURE, NOW);
        when(counter.increment(eq(userId), any())).thenReturn(5L);
        when(repository.findWithLockByUserId(userId)).thenReturn(Optional.of(account));

        assertThat(processor.recordFailure(userId, NOW)).isTrue();
        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void releasesExpiredTemporaryLockAndPublishesAccountUnlocked() {
        UUID userId = UUID.randomUUID();
        AuthAccount account = AuthAccount.create(userId, "lock@example.com");
        account.lockTemporarily(LockReason.CONSECUTIVE_LOGIN_FAILURE, NOW.minus(Duration.ofHours(1)));
        when(repository.findWithLockByUserId(userId)).thenReturn(Optional.of(account));

        assertThat(processor.releaseIfCooldownElapsed(userId, NOW)).isTrue();

        assertThat(account.getLockState()).isEqualTo(LockState.NONE);
        verify(messagePublisher).publish(any(AccountUnlocked.class));
    }

    @Test
    void keepsLockDuringCooldown() {
        UUID userId = UUID.randomUUID();
        AuthAccount account = AuthAccount.create(userId, "lock@example.com");
        account.lockTemporarily(LockReason.CONSECUTIVE_LOGIN_FAILURE, NOW.minusSeconds(60));
        when(repository.findWithLockByUserId(userId)).thenReturn(Optional.of(account));

        assertThat(processor.releaseIfCooldownElapsed(userId, NOW)).isFalse();
        assertThat(account.getLockState()).isEqualTo(LockState.TEMP_LOCKED);
        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void resetFailuresDelegatesToCounter() {
        UUID userId = UUID.randomUUID();

        processor.resetFailures(userId);

        verify(counter).reset(userId);
    }
}
