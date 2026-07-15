package com.example.auth.infra.messaging.config;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.infra.messaging.consume.DeadLetterRetryScheduler;
import com.example.auth.infra.messaging.consume.DeadLetterStore;
import com.example.auth.infra.messaging.consume.EventPayloadCodec;
import com.example.auth.infra.messaging.consume.IntegrationEventDispatcher;
import com.example.auth.infra.messaging.consume.ProcessedEventStore;
import com.example.auth.infra.messaging.publish.InProcessMessagePublisher;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * in-process 메시징(발행 포트·디스패처·디둡·DLQ 재시도)을 조립한다.
 */
@Configuration
@EnableScheduling
public class MessagingConfig {

    @Bean
    public MessagePublisher messagePublisher(ApplicationEventPublisher eventPublisher) {
        return new InProcessMessagePublisher(eventPublisher);
    }

    @Bean
    public EventPayloadCodec eventPayloadCodec() {
        return new EventPayloadCodec();
    }

    @Bean
    public ProcessedEventStore processedEventStore(JdbcTemplate jdbcTemplate) {
        return new ProcessedEventStore(jdbcTemplate);
    }

    @Bean
    public DeadLetterStore deadLetterStore(JdbcTemplate jdbcTemplate) {
        return new DeadLetterStore(jdbcTemplate);
    }

    @Bean
    public IntegrationEventDispatcher integrationEventDispatcher(
            List<IntegrationEventConsumer<?>> consumers,
            ProcessedEventStore processedEventStore,
            DeadLetterStore deadLetterStore,
            EventPayloadCodec codec,
            PlatformTransactionManager transactionManager,
            @Value("${messaging.dlq.base-retry-delay:PT30S}") Duration baseRetryDelay) {
        return new IntegrationEventDispatcher(
                consumers, processedEventStore, deadLetterStore, codec, transactionManager, baseRetryDelay);
    }

    @Bean
    public DeadLetterRetryScheduler deadLetterRetryScheduler(
            IntegrationEventDispatcher dispatcher,
            DeadLetterStore deadLetterStore,
            EventPayloadCodec codec,
            @Value("${messaging.dlq.base-retry-delay:PT30S}") Duration baseRetryDelay,
            @Value("${messaging.dlq.max-retry-delay:PT1H}") Duration maxRetryDelay,
            @Value("${messaging.dlq.retry-batch-size:100}") int batchSize) {
        return new DeadLetterRetryScheduler(
                dispatcher, deadLetterStore, codec, baseRetryDelay, maxRetryDelay, batchSize);
    }
}
