package com.example.auth.infra.messaging.consume;

import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.infra.messaging.consume.DeadLetterStore.DeadLetter;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * DLQ의 재시도 기한 도래 이벤트를 재전달한다 — 성공 시 행 제거, 실패 시 지수 백오프로 다음 기한을 미룬다.
 *
 * <p>재시도도 디스패처의 처리 원장 경로를 지나므로 그 사이 처리된 이벤트는 멱등 스킵 후 제거된다(성공 커밋과
 * 행 제거 사이의 크래시도 다음 재시도에서 이 경로로 수렴한다).
 */
public class DeadLetterRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRetryScheduler.class);
    private static final int MAX_BACKOFF_EXPONENT = 20;

    private final IntegrationEventDispatcher dispatcher;
    private final DeadLetterStore deadLetterStore;
    private final EventPayloadCodec codec;
    private final Duration baseRetryDelay;
    private final Duration maxRetryDelay;
    private final int batchSize;

    public DeadLetterRetryScheduler(
            IntegrationEventDispatcher dispatcher,
            DeadLetterStore deadLetterStore,
            EventPayloadCodec codec,
            Duration baseRetryDelay,
            Duration maxRetryDelay,
            int batchSize) {
        this.dispatcher = dispatcher;
        this.deadLetterStore = deadLetterStore;
        this.codec = codec;
        this.baseRetryDelay = baseRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${messaging.dlq.poll-interval:PT30S}",
            fixedDelayString = "${messaging.dlq.poll-interval:PT30S}")
    void poll() {
        retryDue(Instant.now());
    }

    /**
     * 기준 시각까지 재시도 기한이 도래한 DLQ 행들을 재전달한다.
     */
    public void retryDue(Instant now) {
        for (DeadLetter letter : deadLetterStore.findDue(now, batchSize)) {
            retry(letter, now);
        }
    }

    private void retry(DeadLetter letter, Instant now) {
        IntegrationEventConsumer<?> consumer = dispatcher.findConsumer(letter.consumerId());
        if (consumer == null) {
            // 소비자가 사라진 행은 배선 결함이다. 유실 방지를 위해 행을 유지하고 매 폴마다 드러낸다.
            log.error("DLQ 재시도 불가 — 등록된 소비자 없음 consumerId={} eventType={}", letter.consumerId(), letter.eventType());
            return;
        }
        try {
            IntegrationEvent event = codec.deserialize(letter.payload(), consumer.eventType());
            dispatcher.process(consumer, event);
            deadLetterStore.purge(letter.id());
        } catch (RuntimeException e) {
            int attempts = letter.attempts() + 1;
            deadLetterStore.recordFailure(
                    letter.id(), attempts, IntegrationEventDispatcher.errorSummary(e), now.plus(backoff(attempts)));
            log.warn(
                    "DLQ 재시도 실패 consumerId={} eventType={} attempts={}",
                    letter.consumerId(),
                    letter.eventType(),
                    attempts,
                    e);
        }
    }

    private Duration backoff(int attempts) {
        int exponent = Math.min(attempts - 1, MAX_BACKOFF_EXPONENT);
        Duration delay = baseRetryDelay.multipliedBy(1L << exponent);
        return delay.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : delay;
    }
}
