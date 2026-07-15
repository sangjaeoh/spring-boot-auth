package com.example.auth.domain.auth.port;

import java.time.Duration;

/**
 * 레이트리밋 카운터·쿨다운의 원자 연산 포트다(Redis 카운터+TTL).
 *
 * <p>구현은 저장소 장애 시 fail-closed로 예외를 던진다(허용으로 열리지 않는다) — 레이트리밋은 보안
 * 통제라 장애가 우회로가 되면 안 된다.
 */
public interface RateLimitStore {

    /**
     * 키의 카운터를 1 증가시키고 증가 후 값을 반환한다. 창의 첫 증가가 만료(TTL)를 세팅한다
     * (고정 창 — 창 경과 시 자동 소멸).
     */
    long increment(String key, Duration window);

    /**
     * 쿨다운 표식을 원자 획득한다 — 첫 획득이면 {@code true}, 쿨다운 진행 중(표식 존재)이면
     * {@code false}. 표식은 지속시간 경과 후 자동 소멸한다.
     */
    boolean tryAcquire(String key, Duration cooldown);
}
