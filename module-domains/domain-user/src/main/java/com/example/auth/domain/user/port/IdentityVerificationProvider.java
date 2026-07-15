package com.example.auth.domain.user.port;

import com.example.auth.domain.user.entity.Provider;

/**
 * 외부 본인확인기관 실명확인의 벤더 중립 포트다(유저 도메인 소유, external 모듈이 구현).
 *
 * <p>dev/test는 외부 호출 없는 Mock 어댑터로 동작한다. 온보딩(인증)은 이 포트를 직접 호출하지 않고
 * 유저 도메인({@code IdentityVerificationProcessor})을 경유한다(DOMAIN_MODEL §1.2).
 */
public interface IdentityVerificationProvider {

    /**
     * 이 어댑터가 대변하는 기관을 반환한다.
     */
    Provider provider();

    /**
     * 주장된 정체성을 기관에 실명확인하고 판정 결과를 반환한다.
     */
    IdentityProviderOutcome verify(IdentityProviderRequest request);
}
