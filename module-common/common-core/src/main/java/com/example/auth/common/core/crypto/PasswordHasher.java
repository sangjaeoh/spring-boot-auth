package com.example.auth.common.core.crypto;

/**
 * 비밀번호를 단방향 해시로 저장·대조하는 벤더 중립 원자재다.
 *
 * <p>도메인 의미가 없는 커모디티 암호 원자재라 포트를 common-core가 소유하고 infra가 구현한다
 * (docs/architecture.md — infra vs external). 원문·가역 암호를 반환하지 않는다.
 */
public interface PasswordHasher {

    /**
     * 원문 비밀번호를 안전 해시 문자열로 인코딩해 반환한다.
     */
    String hash(String rawPassword);

    /**
     * 원문이 인코딩된 해시와 일치하는지 상수시간에 가깝게 대조한다.
     */
    boolean matches(String rawPassword, String encodedHash);

    /**
     * 이 해시가 사용하는 알고리즘 식별자를 반환한다(예: {@code ARGON2ID}).
     */
    String algorithm();
}
