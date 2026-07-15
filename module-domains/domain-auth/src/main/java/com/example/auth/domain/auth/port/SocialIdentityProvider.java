package com.example.auth.domain.auth.port;

import com.example.auth.domain.auth.entity.SocialProvider;

/**
 * 소셜 IdP {@code id_token} 검증 포트다(OIDC 커모디티 — 검증된 라이브러리 위탁, dev/test는 Mock).
 *
 * <p>구현은 서명·발급자·수신자·만료를 검증하고 subject·email(릴레이 여부)만 반환한다. 검증 실패는
 * 예외가 아니라 {@link SocialIdentityOutcome.Failed}로 표현한다 — 위조 토큰은 예외적 상황이 아니라
 * 일상 입력이다.
 */
public interface SocialIdentityProvider {

    /**
     * 제공자의 {@code id_token}을 검증하고 검증된 신원 또는 실패를 반환한다.
     */
    SocialIdentityOutcome verify(SocialProvider provider, String idToken);
}
