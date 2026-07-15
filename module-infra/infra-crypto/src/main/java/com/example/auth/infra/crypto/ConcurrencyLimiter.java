package com.example.auth.infra.crypto;

import com.example.auth.common.core.exception.HashingCapacityException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 동시 실행 수를 상한해 CPU·메모리 집약 작업의 폭주를 차단한다(공정 세마포어 백프레셔).
 *
 * <p>permit을 제한 시간 안에 확보하지 못하면 {@link HashingCapacityException}으로 부하를 차단한다.
 * 획득에 성공한 경우에만 permit을 반환하므로 거부·인터럽트가 permit을 누수시키지 않는다(누수 시 상한이
 * 조용히 상승해 게이트가 무력화된다).
 */
class ConcurrencyLimiter {

    private final Semaphore permits;
    private final Duration acquireTimeout;

    ConcurrencyLimiter(int maxConcurrent, Duration acquireTimeout) {
        this.permits = new Semaphore(maxConcurrent, true);
        this.acquireTimeout = acquireTimeout;
    }

    /**
     * permit을 확보한 뒤 작업을 실행하고 결과를 반환한다. 확보 실패 시 {@link HashingCapacityException}.
     */
    <T> T call(Supplier<T> task) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 인터럽트는 용량 초과가 아니다 — 플래그를 복원해 전파하고 503으로 마스킹하지 않는다.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("해시 슬롯 대기 중 인터럽트됨", e);
        }
        if (!acquired) {
            throw new HashingCapacityException();
        }
        try {
            return task.get();
        } finally {
            permits.release();
        }
    }

    /**
     * 현재 사용 가능한 permit 수를 반환한다(테스트 관측용 — permit 누수 검증).
     */
    int availablePermits() {
        return permits.availablePermits();
    }
}
