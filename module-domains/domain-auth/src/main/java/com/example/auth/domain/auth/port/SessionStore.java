package com.example.auth.domain.auth.port;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 세션(AccountSessions 애그리거트)의 원자 영속 포트다.
 *
 * <p>Redis 애그리거트라 Spring Data 포트가 성립하지 않는 예외 지점이다(IMPLEMENTATION_PLAN §1) — 도메인이
 * 포트를 선언하고 infra-redis가 Lua 원자성으로 구현한다. 모든 연산은 {@code userId} 슬롯 국소라 Cluster
 * 원자성을 보존한다.
 *
 * <p>저장소 불가 시 페일 모드는 판정 지점별로 갈린다 — {@link #validate}는 fail-closed로 {@code false}를
 * 반환해 서명이 유효한 토큰도 거부되게 하고(가용성보다 실시간 무효화), 그 외 연산은
 * {@code AuthErrorCode.SESSION_STORE_UNAVAILABLE}(503)로 실패해 토큰을 발급하지 않는다(재시도 가능·유효
 * 토큰 미폐기). revoke 계열은 구성된 최소 복제 확인({@code WAIT})까지 같은 페일 모드로 강제해 failover 시
 * 무효화 유실을 차단한다.
 */
public interface SessionStore {

    /**
     * 활성 세션을 생성하고 리프레시 역인덱스를 세운다. 동시 활성 세션이 {@code maxSessions}를 넘지 않게
     * 원자 검증하고, 초과분은 발급 오래된 순으로 축출해 그 세션 ID들을 반환한다.
     */
    List<UUID> create(
            UUID userId,
            UUID sessionId,
            UUID deviceId,
            String ip,
            @Nullable String userAgent,
            String refreshJtiHash,
            String refreshPlain,
            Instant now,
            Duration sessionTtl,
            int maxSessions);

    /**
     * 세션이 현재 활성(미폐기·미만료)인지 O(1)로 검증한다.
     */
    boolean validate(UUID userId, UUID sessionId, Instant now);

    /**
     * 사용자의 활성 세션 스냅샷을 발급 오래된 순으로 반환한다.
     */
    List<SessionSnapshot> findAllActive(UUID userId, Instant now);

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

    /**
     * 지정 세션을 제외한 사용자의 전체 세션을 무효화한다.
     */
    void revokeAllExcept(UUID userId, UUID keepSessionId);

    /**
     * 해당 기기에 바인딩된 사용자의 세션을 무효화한다.
     */
    void revokeByDevice(UUID userId, UUID deviceId);
}
