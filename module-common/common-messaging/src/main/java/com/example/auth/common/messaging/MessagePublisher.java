package com.example.auth.common.messaging;

/**
 * 통합 이벤트 발행 포트다(구현은 infra-messaging — 현재 in-process transport).
 *
 * <p>활성 트랜잭션 안에서 발행하면 커밋 후에 전달되고 롤백 시 전달되지 않는다. 활성 트랜잭션이 없으면 즉시
 * 전달된다. 전달은 at-least-once이므로 소비자는 {@link IntegrationEventConsumer} 규약에 따라 멱등해야 한다.
 */
public interface MessagePublisher {

    /**
     * 통합 이벤트를 발행한다.
     */
    void publish(IntegrationEvent event);
}
