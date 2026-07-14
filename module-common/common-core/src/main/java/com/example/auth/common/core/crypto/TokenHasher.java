package com.example.auth.common.core.crypto;

/**
 * 토큰 원문을 결정적 해시로 변환하는 벤더 중립 원자재다.
 *
 * <p>리프레시 토큰 등 원문 미저장 대상의 조회·대조 키를 만든다. 같은 입력은 같은 해시를 낸다
 * (blind lookup용). 비밀번호에는 {@link PasswordHasher}(salt·느린 KDF)를 쓴다.
 */
public interface TokenHasher {

    /**
     * 토큰 원문을 hex 인코딩된 결정적 해시로 반환한다.
     */
    String hash(String rawToken);
}
