package com.example.auth.infra.messaging.publish;

import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.common.messaging.MessagePublisher;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link MessagePublisher}의 in-process 구현 — 이벤트를 Spring 이벤트 버스에 싣는다.
 *
 * <p>커밋 후 전달·롤백 시 미전달은 디스패처의 {@code @TransactionalEventListener(AFTER_COMMIT)}가 보장한다.
 * plain {@code @EventListener} 구독자(구조적 로깅 등)는 종전대로 발행 즉시 수신한다 — 디둡·DLQ 계약 밖이다.
 */
public class InProcessMessagePublisher implements MessagePublisher {

    private final ApplicationEventPublisher eventPublisher;

    public InProcessMessagePublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(IntegrationEvent event) {
        eventPublisher.publishEvent(event);
    }
}
