package com.example.auth.infra.messaging.consume;

import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.common.messaging.IntegrationEventConsumer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 통합 이벤트를 등록된 {@link IntegrationEventConsumer}에 전달한다 — 커밋 후 전달·멱등·실패 시 DLQ.
 *
 * <p>발행 트랜잭션이 있으면 커밋 후에만 전달하고 롤백 시 전달하지 않는다. 트랜잭션 밖 발행은 즉시 전달한다.
 * 소비자별 처리 원장 기록과 {@code consume}을 하나의 신규 트랜잭션으로 묶어 이벤트당 1회 처리를 강제하고,
 * 소비 예외는 DLQ에 적재해 재시도로 넘긴다. 한 소비자의 실패는 다른 소비자 전달을 막지 않는다.
 */
public class IntegrationEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(IntegrationEventDispatcher.class);

    private final Map<Class<? extends IntegrationEvent>, List<IntegrationEventConsumer<?>>> consumersByType;
    private final Map<String, IntegrationEventConsumer<?>> consumersById;
    private final ProcessedEventStore processedEventStore;
    private final DeadLetterStore deadLetterStore;
    private final EventPayloadCodec codec;
    private final TransactionTemplate newTransaction;
    private final Duration firstRetryDelay;

    public IntegrationEventDispatcher(
            List<IntegrationEventConsumer<?>> consumers,
            ProcessedEventStore processedEventStore,
            DeadLetterStore deadLetterStore,
            EventPayloadCodec codec,
            PlatformTransactionManager transactionManager,
            Duration firstRetryDelay) {
        this.consumersById = indexById(consumers);
        this.consumersByType = indexByType(consumers);
        this.processedEventStore = processedEventStore;
        this.deadLetterStore = deadLetterStore;
        this.codec = codec;
        // AFTER_COMMIT 단계는 완료된 트랜잭션 리소스가 스레드에 남아 있으므로 신규 트랜잭션으로 분리한다.
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.firstRetryDelay = firstRetryDelay;
    }

    /**
     * 발행 트랜잭션 커밋 후(트랜잭션 밖 발행은 즉시) 이벤트를 구독 소비자들에 전달한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void dispatch(IntegrationEvent event) {
        for (IntegrationEventConsumer<?> consumer : consumersByType.getOrDefault(event.getClass(), List.of())) {
            deliver(consumer, event);
        }
    }

    /**
     * 처리 원장 기록과 소비를 하나의 신규 트랜잭션으로 수행한다. 이미 처리된 이벤트는 소비 없이 반환한다.
     *
     * @throws RuntimeException 소비 실패 시(트랜잭션은 원장 기록까지 롤백된 상태)
     */
    void process(IntegrationEventConsumer<?> consumer, IntegrationEvent event) {
        newTransaction.executeWithoutResult(status -> {
            if (!processedEventStore.tryMarkProcessed(consumer.consumerId(), event.eventId(), Instant.now())) {
                log.info(
                        "중복 전달 스킵 consumerId={} eventType={} eventId={}",
                        consumer.consumerId(),
                        event.eventType(),
                        event.eventId());
                return;
            }
            consumeUnchecked(consumer, event);
        });
    }

    /**
     * consumerId로 등록 소비자를 찾는다(DLQ 재시도용). 없으면 null.
     */
    @Nullable
    IntegrationEventConsumer<?> findConsumer(String consumerId) {
        return consumersById.get(consumerId);
    }

    private void deliver(IntegrationEventConsumer<?> consumer, IntegrationEvent event) {
        try {
            process(consumer, event);
        } catch (RuntimeException e) {
            log.warn(
                    "이벤트 소비 실패 — DLQ 적재 consumerId={} eventType={} eventId={}",
                    consumer.consumerId(),
                    event.eventType(),
                    event.eventId(),
                    e);
            enqueueDeadLetter(consumer, event, e);
        }
    }

    private void enqueueDeadLetter(IntegrationEventConsumer<?> consumer, IntegrationEvent event, RuntimeException e) {
        try {
            String payload = codec.serialize(event);
            newTransaction.executeWithoutResult(status -> deadLetterStore.enqueue(
                    consumer.consumerId(),
                    event,
                    payload,
                    errorSummary(e),
                    Instant.now().plus(firstRetryDelay)));
        } catch (RuntimeException enqueueFailure) {
            log.error(
                    "DLQ 적재 실패 — 이벤트 유실 consumerId={} eventType={} eventId={}",
                    consumer.consumerId(),
                    event.eventType(),
                    event.eventId(),
                    enqueueFailure);
        }
    }

    static String errorSummary(RuntimeException e) {
        String message = e.getMessage();
        return e.getClass().getName() + (message == null ? "" : ": " + message);
    }

    // 레지스트리가 eventType()을 키로 매칭하므로 이 캐스트는 등록 시점에 보장된다.
    @SuppressWarnings("unchecked")
    private static void consumeUnchecked(IntegrationEventConsumer<?> consumer, IntegrationEvent event) {
        ((IntegrationEventConsumer<IntegrationEvent>) consumer).consume(event);
    }

    private static Map<String, IntegrationEventConsumer<?>> indexById(List<IntegrationEventConsumer<?>> consumers) {
        Map<String, IntegrationEventConsumer<?>> byId = new HashMap<>();
        for (IntegrationEventConsumer<?> consumer : consumers) {
            IntegrationEventConsumer<?> duplicate = byId.putIfAbsent(consumer.consumerId(), consumer);
            if (duplicate != null) {
                throw new IllegalStateException("consumerId 중복 — 디둡 원장이 오염된다: " + consumer.consumerId());
            }
        }
        return byId;
    }

    private static Map<Class<? extends IntegrationEvent>, List<IntegrationEventConsumer<?>>> indexByType(
            List<IntegrationEventConsumer<?>> consumers) {
        Map<Class<? extends IntegrationEvent>, List<IntegrationEventConsumer<?>>> byType = new HashMap<>();
        for (IntegrationEventConsumer<?> consumer : consumers) {
            byType.computeIfAbsent(consumer.eventType(), type -> new ArrayList<>())
                    .add(consumer);
        }
        return byType;
    }
}
