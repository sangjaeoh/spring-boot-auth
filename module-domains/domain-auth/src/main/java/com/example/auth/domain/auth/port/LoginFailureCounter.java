package com.example.auth.domain.auth.port;

import java.time.Duration;
import java.util.UUID;

/**
 * 연속 로그인 실패 카운터의 원자 연산 포트다(Redis 카운터+TTL — 이력(RDB)과 분리, DOMAIN_MODEL §2.6).
 *
 * <p>구현은 저장소 장애 시 fail-closed로 예외를 던진다 — 카운터 유실이 잠금 우회로가 되지 않게 한다.
 */
public interface LoginFailureCounter {

    /**
     * 계정의 실패 카운터를 1 증가시키고 증가 후 값을 반환한다. 창의 첫 증가가 만료(TTL)를 세팅한다.
     */
    long increment(UUID userId, Duration window);

    /**
     * 계정의 실패 카운터를 제거한다(성공 로그인·잠금 확정 시).
     */
    void reset(UUID userId);
}
