package com.example.auth.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.infra.messaging.config.MessagingConfig;
import com.example.auth.infra.messaging.consume.DeadLetterRetryScheduler;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * in-process 메시징 IT: 롤백 시 미전달, AFTER_COMMIT 전달 순서, 중복 전달 디둡, 소비 실패 → DLQ 적재 →
 * 재시도 성공을 실 PostgreSQL(msg 스키마)로 검증한다.
 */
@SpringBootTest
@Import({MessagingConfig.class, InProcessMessagingIT.TestConsumers.class})
@Testcontainers
class InProcessMessagingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private DeadLetterRetryScheduler retryScheduler;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingConsumer recordingConsumer;

    @Autowired
    private FlakyConsumer flakyConsumer;

    @BeforeEach
    void resetConsumers() {
        recordingConsumer.consumed.clear();
        flakyConsumer.attempts.set(0);
    }

    @Test
    void doesNotDeliverWhenPublishingTransactionRollsBack() {
        SampleOccurred event = new SampleOccurred("롤백 대상");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    messagePublisher.publish(event);
                    throw new IllegalStateException("발행 트랜잭션 롤백");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(recordingConsumer.consumed).isEmpty();
        assertThat(processedCount(event.eventId())).isZero();
        assertThat(deadLetterCount(event.eventId())).isZero();
    }

    @Test
    void deliversAfterCommitInPublishOrder() {
        SampleOccurred first = new SampleOccurred("첫째");
        SampleOccurred second = new SampleOccurred("둘째");

        transactionTemplate.executeWithoutResult(status -> {
            messagePublisher.publish(first);
            messagePublisher.publish(second);
            // 커밋 전에는 전달되지 않는다(AFTER_COMMIT).
            assertThat(recordingConsumer.consumed).isEmpty();
        });

        assertThat(recordingConsumer.consumed).containsExactly(first, second);
    }

    @Test
    void processesDuplicateDeliveryOnlyOnce() {
        SampleOccurred event = new SampleOccurred("중복 전달");

        messagePublisher.publish(event);
        messagePublisher.publish(event);

        assertThat(recordingConsumer.consumed).containsExactly(event);
        assertThat(processedCount(event.eventId())).isEqualTo(1);
    }

    @Test
    void retriesFromDeadLetterUntilConsumeSucceeds() {
        FragileOccurred event = new FragileOccurred("한 번 실패 후 성공");
        flakyConsumer.failuresBeforeSuccess = 1;

        messagePublisher.publish(event);

        // 소비 실패 — 처리 원장은 롤백되고 DLQ에 내구 적재된다.
        assertThat(flakyConsumer.attempts).hasValue(1);
        assertThat(processedCount(event.eventId())).isZero();
        assertThat(deadLetterCount(event.eventId())).isEqualTo(1);

        // 재시도 기한 도래 — 재전달 성공 시 DLQ 행이 제거되고 처리 원장이 남는다.
        retryScheduler.retryDue(Instant.now().plus(Duration.ofDays(1)));

        assertThat(flakyConsumer.attempts).hasValue(2);
        assertThat(processedCount(event.eventId())).isEqualTo(1);
        assertThat(deadLetterCount(event.eventId())).isZero();
    }

    private int processedCount(UUID eventId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from msg.processed_event where event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private int deadLetterCount(UUID eventId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from msg.dead_letter_event where event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    static class RecordingConsumer implements IntegrationEventConsumer<SampleOccurred> {

        final List<SampleOccurred> consumed = new CopyOnWriteArrayList<>();

        @Override
        public String consumerId() {
            return "recording-consumer";
        }

        @Override
        public Class<SampleOccurred> eventType() {
            return SampleOccurred.class;
        }

        @Override
        public void consume(SampleOccurred event) {
            consumed.add(event);
        }
    }

    static class FlakyConsumer implements IntegrationEventConsumer<FragileOccurred> {

        final AtomicInteger attempts = new AtomicInteger();
        volatile int failuresBeforeSuccess = 1;

        @Override
        public String consumerId() {
            return "flaky-consumer";
        }

        @Override
        public Class<FragileOccurred> eventType() {
            return FragileOccurred.class;
        }

        @Override
        public void consume(FragileOccurred event) {
            if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                throw new IllegalStateException("일시 소비 오류");
            }
        }
    }

    @TestConfiguration
    static class TestConsumers {

        @Bean
        RecordingConsumer recordingConsumer() {
            return new RecordingConsumer();
        }

        @Bean
        FlakyConsumer flakyConsumer() {
            return new FlakyConsumer();
        }
    }
}
