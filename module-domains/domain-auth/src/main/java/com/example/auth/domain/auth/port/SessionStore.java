package com.example.auth.domain.auth.port;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 세션(AccountSessions 애그리거트)의 원자 영속 포트다.
 *
 * <p>Redis 애그리거트라 Spring Data 포트가 성립하지 않는 예외 지점이다(IMPLEMENTATION_PLAN §1) — 도메인이
 * 포트를 선언하고 infra-redis가 Lua 원자성으로 구현한다. 모든 연산은 {@code userId} 슬롯 국소라 Cluster
 * 원자성을 보존한다. 저장소 예외는 fail-closed(검증 실패)로 처리한다.
 */
public interface SessionStore {

    /**
     * 활성 세션을 생성하고 리프레시 역인덱스를 세운다.
     */
    void create(
            UUID userId,
            UUID sessionId,
            @Nullable UUID deviceId,
            String ip,
            @Nullable String userAgent,
            String refreshJtiHash,
            String refreshPlain,
            Instant now,
            Duration sessionTtl);

    /**
     * 세션이 현재 활성(미폐기·미만료)인지 O(1)로 검증한다.
     */
    boolean validate(UUID userId, UUID sessionId, Instant now);

    /**
     * 제시된 리프레시를 회전하거나, 유예 재시도·재사용·무효를 판정한다.
     */
    RotationResult rotate(
            String presentedJtiHash,
            String newJtiHash,
            String newRefreshPlain,
            Instant now,
            Duration graceWindow,
            Duration sessionTtl);

    /**
     * 단일 세션을 무효화한다(로그아웃).
     */
    void revoke(UUID userId, UUID sessionId);

    /**
     * 사용자의 전체 세션을 무효화한다(강제 종료·재사용 감지).
     */
    void revokeAll(UUID userId);
}
