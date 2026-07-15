package com.example.auth.infra.messaging.consume;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.infra.messaging.SampleOccurred;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * 디스패처 조립 검증: consumerId 중복은 디둡 원장을 오염시키므로 기동을 실패시킨다.
 */
class IntegrationEventDispatcherTest {

    @Test
    void rejectsDuplicateConsumerIds() {
        List<IntegrationEventConsumer<?>> duplicated = List.of(new NamedConsumer(), new NamedConsumer());

        assertThatThrownBy(() -> new IntegrationEventDispatcher(
                        duplicated,
                        new ProcessedEventStore(new JdbcTemplate()),
                        new DeadLetterStore(new JdbcTemplate()),
                        new EventPayloadCodec(),
                        new NoopTransactionManager(),
                        Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consumerId 중복");
    }

    static class NamedConsumer implements IntegrationEventConsumer<SampleOccurred> {

        @Override
        public String consumerId() {
            return "duplicated-consumer";
        }

        @Override
        public Class<SampleOccurred> eventType() {
            return SampleOccurred.class;
        }

        @Override
        public void consume(SampleOccurred event) {}
    }

    static class NoopTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(@Nullable TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }
}
