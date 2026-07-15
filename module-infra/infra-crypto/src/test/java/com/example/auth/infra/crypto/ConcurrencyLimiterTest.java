package com.example.auth.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.exception.HashingCapacityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcurrencyLimiterTest {

    @Test
    void capsConcurrentExecutionsToPermits() throws Exception {
        int permits = 2;
        int tasks = 6; // permits의 배수 — 각 웨이브가 정확히 permits개로 채워진다
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(permits, Duration.ofSeconds(5));
        ExecutorService pool = Executors.newFixedThreadPool(tasks);
        CountDownLatch start = new CountDownLatch(1);
        CyclicBarrier waveFull = new CyclicBarrier(permits);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < tasks; i++) {
                futures.add(pool.submit(() -> {
                    awaitQuietly(start);
                    return limiter.call(() -> {
                        int current = active.incrementAndGet();
                        maxObserved.accumulateAndGet(current, Math::max);
                        // 같은 웨이브 permits개가 모두 임계구역에 든 뒤에야 진행 — 동시성이 정확히 permits에
                        // 도달함을 강제한다(과소제한이면 홀로 대기하다 타임아웃, 과대제한이면 maxObserved 초과).
                        awaitBarrier(waveFull);
                        active.decrementAndGet();
                        return Boolean.TRUE;
                    });
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(maxObserved.get()).isEqualTo(permits);
    }

    @Test
    void shedsAndDoesNotLeakPermitsWhenSaturated() throws Exception {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(1, Duration.ofMillis(100));
        CountDownLatch holderEntered = new CountDownLatch(1);
        CountDownLatch holderRelease = new CountDownLatch(1);
        Thread holder = startHolder(limiter, holderEntered, holderRelease);
        try {
            holderEntered.await();

            assertThatThrownBy(() -> limiter.call(() -> Boolean.TRUE)).isInstanceOf(HashingCapacityException.class);
            // 거부된 acquire는 permit을 반환하지 않는다 — 반환 시 상한이 조용히 상승해 게이트가 무력화된다.
            assertThat(limiter.availablePermits()).isZero();
        } finally {
            holderRelease.countDown();
            holder.join(2000);
        }

        assertThat(limiter.availablePermits()).isEqualTo(1);
    }

    @Test
    void releasesPermitWhenTaskThrows() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(1, Duration.ofSeconds(1));

        assertThatThrownBy(() -> limiter.call(() -> {
                    throw new RuntimeException("boom");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        assertThat(limiter.availablePermits()).isEqualTo(1);
        assertThat(limiter.call(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void restoresInterruptFlagAndDoesNotLeakOnInterrupt() throws Exception {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(1, Duration.ofSeconds(5));
        CountDownLatch holderEntered = new CountDownLatch(1);
        CountDownLatch holderRelease = new CountDownLatch(1);
        Thread holder = startHolder(limiter, holderEntered, holderRelease);
        Throwable[] thrown = new Throwable[1];
        boolean[] interruptFlag = new boolean[1];
        try {
            holderEntered.await();

            Thread worker = new Thread(() -> {
                try {
                    limiter.call(() -> Boolean.TRUE);
                } catch (Throwable t) {
                    thrown[0] = t;
                    interruptFlag[0] = Thread.currentThread().isInterrupted();
                }
            });
            worker.start();
            sleepQuietly(100);
            worker.interrupt();
            worker.join(2000);

            assertThat(thrown[0]).isInstanceOf(IllegalStateException.class);
            assertThat(interruptFlag[0]).isTrue();
            // 인터럽트는 permit을 획득하지 못했으므로 반환하지 않는다.
            assertThat(limiter.availablePermits()).isZero();
        } finally {
            holderRelease.countDown();
            holder.join(2000);
        }
    }

    private static Thread startHolder(ConcurrencyLimiter limiter, CountDownLatch entered, CountDownLatch release) {
        Thread holder = new Thread(() -> limiter.call(() -> {
            entered.countDown();
            awaitQuietly(release);
            return Boolean.TRUE;
        }));
        holder.start();
        return holder;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (BrokenBarrierException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }
}
