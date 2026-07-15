package com.example.auth.common.messaging;

/**
 * 통합 이벤트 소비자 규약이다. 구현 빈은 infra-messaging 디스패처가 발견해 전달한다.
 *
 * <p>소비 계약:
 *
 * <ul>
 *   <li>전달은 at-least-once다. 디스패처가 {@code (consumerId, eventId)} 처리 원장(디둡)으로 이벤트당 1회
 *       처리를 강제하므로, 중복 전달은 {@link #consume(IntegrationEvent)} 호출 없이 스킵된다.
 *   <li>{@code consume}은 디스패처가 여는 신규 트랜잭션 안에서 호출된다. 같은 DataSource에 대한 쓰기는 처리
 *       원장 기록과 원자적으로 커밋된다. 트랜잭션 밖 부수효과(로그·외부 호출)는 실패 재시도 시 반복될 수 있다.
 *   <li>{@code consume}이 예외를 던지면 트랜잭션(처리 원장 포함)이 롤백되고 이벤트는 DLQ에 적재돼 백오프로
 *       재시도된다. 일시 오류는 예외로 전파하고, 영구 무효 이벤트는 정상 반환으로 소진해야 한다.
 *   <li>정상 경로의 전달 순서는 같은 userId에 대해 발행 순서를 따른다(in-process 동기 전달). 단 DLQ 재시도
 *       재전달은 원 순서 밖에서 도착할 수 있으므로 순서 역전을 허용하도록 구현해야 한다.
 * </ul>
 */
public interface IntegrationEventConsumer<E extends IntegrationEvent> {

    /**
     * 디둡·DLQ 귀속 단위가 되는 안정 식별자를 반환한다. 컨텍스트 전체에서 유일해야 하며(기동 시 검증), 바꾸면
     * 처리 이력이 단절돼 과거 이벤트가 재처리될 수 있다.
     */
    String consumerId();

    /**
     * 구독하는 통합 이벤트 타입을 반환한다.
     */
    Class<E> eventType();

    /**
     * 이벤트를 처리한다.
     */
    void consume(E event);
}
