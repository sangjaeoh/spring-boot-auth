package com.example.auth.domain.auth.entity;

/**
 * 온보딩 중 버퍼링되는 동의 선택값이다(가입 완료 전 {@code ConsentRecord} 선-기록 금지 — userId가 없다).
 *
 * <p>약관 유형은 유저 도메인 소유 개념이라 인증은 문자열로 opaque 보관한다(크로스 seam enum 공유 금지).
 * 인증 세션은 버퍼 값의 유효성을 보증하지 않는다 — 버퍼 전 조기 검증은 파사드가 유저 도메인으로 수행하고,
 * 최종 권위는 {@code CreateUser} 트랜잭션의 재검증이다.
 */
public record ConsentSelection(String termsType, int termsVersion) {

    public ConsentSelection {
        if (termsType.isBlank()) {
            throw new IllegalArgumentException("termsType은 비어 있을 수 없습니다.");
        }
    }
}
