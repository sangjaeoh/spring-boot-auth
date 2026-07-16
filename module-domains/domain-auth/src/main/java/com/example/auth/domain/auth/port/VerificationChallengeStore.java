package com.example.auth.domain.auth.port;

import java.time.Duration;
import java.util.UUID;

/**
 * 원타임 인증코드(VerificationChallenge 애그리거트)의 원자 영속 포트다.
 *
 * <p>세션과 같은 Redis-TTL 애그리거트라 도메인이 포트를 선언하고 infra-redis가 Lua 원자성으로 구현한다
 * (IMPLEMENTATION_PLAN §1). {@link #verify}는 존재·시도상한·코드대조·시도증가·성공시 소진(single-use)을 한
 * 번의 원자 연산으로 수행해 저엔트로피 코드의 시도상한 우회(경합)를 막는다. 저장소 불가 시 {@link #issue}는
 * {@code AuthErrorCode.SESSION_STORE_UNAVAILABLE}(503)로 fail-closed하고, {@link #verify}는 NOT_FOUND로
 * 거부한다.
 */
public interface VerificationChallengeStore {

    /**
     * 인증코드 챌린지를 저장하고 TTL을 건다. {@code subjectId}는 검증 성공 시 되돌려줄 대상이다.
     */
    void issue(String challengeId, UUID subjectId, String codeHash, Duration ttl, int maxAttempts);

    /**
     * 제시된 코드 해시를 원자 검증한다. 일치·미소진이면 챌린지를 소진하고 {@code subjectId}를 반환한다.
     */
    VerificationResult verify(String challengeId, String codeHash);
}
