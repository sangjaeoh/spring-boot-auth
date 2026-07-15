package com.example.auth.common.core.crypto;

/**
 * 암호화 저장된 PII의 동등 조회를 가능케 하는 결정적 blind index를 만드는 벤더 중립 원자재다.
 *
 * <p>암호문은 매번 달라(random IV) 동등 조회가 불가능하므로, 조회가 필요한 PII(전화 등)는 별도의 결정적
 * 파생값을 함께 저장한다. blind index는 keyed HMAC(비밀 pepper)이라 같은 입력은 같은 출력을 낸다. 보안은
 * 전적으로 <b>pepper 비밀성</b>에 의존한다 — 대상 값(전화 등)은 열거 가능한 저엔트로피이므로, pepper가
 * 암호문 저장소와 분리 보관될 때만 역추론이 막힌다. 출력에 pepper 버전을 동봉해 회전 seam을 남긴다.
 */
public interface BlindIndexer {

    /**
     * 값의 결정적 blind index(pepper 버전 동봉)를 반환한다. 같은 입력은 같은 출력을 낸다.
     */
    String blindIndex(String value);
}
