package com.example.auth.domain.auth.port;

import java.util.Optional;

/**
 * IP의 지리 위치를 조회하는 벤더 중립 포트다(위험도 평가의 신규 지역 판정 입력).
 *
 * <p>인증 도메인이 소비하므로 도메인이 포트를 소유하고 external이 구현한다(docs/architecture.md — infra vs
 * external: GeoIP는 벤더 교체 대상 external). dev/test는 Mock 어댑터로 오프라인 검증한다.
 */
public interface GeoIpLookup {

    /**
     * IP의 지리 위치를 반환한다. 판정 불가(사설망·미등록 대역·조회 실패)는 빈 값이다 — 호출자는
     * 위치 신호 없이 평가를 계속한다.
     */
    Optional<GeoLocation> lookup(String ip);
}
