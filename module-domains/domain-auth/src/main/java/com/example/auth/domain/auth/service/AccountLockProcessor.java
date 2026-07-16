package com.example.auth.domain.auth.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.LockReason;
import com.example.auth.domain.auth.entity.LockState;
import com.example.auth.domain.auth.event.AccountLocked;
import com.example.auth.domain.auth.event.AccountUnlocked;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.LockReleaseInfo;
import com.example.auth.domain.auth.port.LoginFailureCounter;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연속 실패 잠금 정책({@code AccountLockPolicy})의 상태 전이를 소유한다 — 실패 카운터(Redis+TTL,
 * 이력과 분리) 임계 도달 시 일시 잠금(TEMP_LOCKED)·{@link AccountLocked} 발행, 쿨다운 경과 시 해제,
 * 성공 로그인 시 카운터 리셋.
 *
 * <p>카운터 창은 쿨다운과 같다 — 창 경과 시 카운터가 자동 소멸해 "연속" 실패만 잠금으로 이어진다.
 * 관리자 잠금(ADMIN_LOCKED)의 전이·해제도 함께 소유한다(쿨다운 자동 해제 비대상 — 관리자 해제만).
 */
@Service
public class AccountLockProcessor {

    private final AuthAccountRepository authAccountRepository;
    private final LoginFailureCounter loginFailureCounter;
    private final MessagePublisher messagePublisher;
    private final int failureThreshold;
    private final Duration cooldown;

    public AccountLockProcessor(
            AuthAccountRepository authAccountRepository,
            LoginFailureCounter loginFailureCounter,
            MessagePublisher messagePublisher,
            @Value("${auth.lock.failure-threshold:5}") int failureThreshold,
            @Value("${auth.lock.cooldown-seconds:1800}") long cooldownSeconds) {
        this.authAccountRepository = authAccountRepository;
        this.loginFailureCounter = loginFailureCounter;
        this.messagePublisher = messagePublisher;
        this.failureThreshold = failureThreshold;
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    /**
     * 자격증명 실패를 카운트하고 임계 도달 시 일시 잠금으로 전이한다. 잠금 상태(신규 잠금·기존 잠금)면
     * {@code true}를 반환한다.
     */
    @Transactional
    public boolean recordFailure(UUID userId, Instant now) {
        long failures = loginFailureCounter.increment(userId, cooldown);
        if (failures < failureThreshold) {
            return false;
        }
        AuthAccount account = authAccountRepository.findWithLockByUserId(userId).orElse(null);
        if (account == null) {
            return false;
        }
        if (account.getLockState() != LockState.NONE) {
            // 이미 잠김(경합·재시도) — 전이·이벤트 없이 멱등 흡수.
            return true;
        }
        account.lockTemporarily(LockReason.CONSECUTIVE_LOGIN_FAILURE, now);
        // 잠금이 확정됐으니 카운터를 소진한다 — 해제 후 실패는 새 창에서 다시 센다.
        loginFailureCounter.reset(userId);
        messagePublisher.publish(new AccountLocked(userId, now));
        return true;
    }

    /**
     * 성공 로그인에서 실패 카운터를 리셋한다.
     */
    public void resetFailures(UUID userId) {
        loginFailureCounter.reset(userId);
    }

    /**
     * 관리자 명령으로 계정을 잠근다(NONE → ADMIN_LOCKED). {@link AccountLocked}를 발행한다.
     *
     * @throws AuthException 미존재 계정(404), 이미 잠긴 계정이면(409)
     */
    @Transactional
    public void lockByAdmin(UUID userId, Instant now) {
        AuthAccount account = getWithLock(userId);
        account.lockByAdmin(now);
        messagePublisher.publish(new AccountLocked(userId, now));
    }

    /**
     * 관리자 명령으로 잠금을 해제하고(TEMP_LOCKED·ADMIN_LOCKED → NONE) 해제 직전 잠금 스냅샷을
     * 반환한다. {@link AccountUnlocked}를 발행한다.
     *
     * @throws AuthException 미존재 계정(404), 잠기지 않은 계정이면(409)
     */
    @Transactional
    public LockReleaseInfo unlockByAdmin(UUID userId, Instant now) {
        AuthAccount account = getWithLock(userId);
        LockReleaseInfo released = new LockReleaseInfo(account.getLockState(), account.getLockReason());
        account.releaseLockByAdmin();
        messagePublisher.publish(new AccountUnlocked(userId, now));
        return released;
    }

    private AuthAccount getWithLock(UUID userId) {
        return authAccountRepository
                .findWithLockByUserId(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_NOT_FOUND));
    }

    /**
     * 쿨다운이 경과한 일시 잠금을 해제한다(로그인 접근 판정 직전의 lazy 해제 — 관리자 잠금은 대상이
     * 아니다). 해제했으면 {@code true}를 반환한다.
     */
    @Transactional
    public boolean releaseIfCooldownElapsed(UUID userId, Instant now) {
        AuthAccount account = authAccountRepository.findWithLockByUserId(userId).orElse(null);
        if (account == null || !account.isTemporaryLockExpired(now, cooldown)) {
            return false;
        }
        account.releaseTemporaryLock();
        messagePublisher.publish(new AccountUnlocked(userId, now));
        return true;
    }
}
