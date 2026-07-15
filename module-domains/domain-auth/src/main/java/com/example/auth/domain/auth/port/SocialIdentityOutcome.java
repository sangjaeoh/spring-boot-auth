package com.example.auth.domain.auth.port;

import org.jspecify.annotations.Nullable;

/**
 * {@code id_token} 검증 결과다.
 *
 * <p>{@code email}은 IdP가 제공하지 않을 수 있다(카카오 미동의 등). {@code privateRelayEmail}은 애플
 * 프라이빗 릴레이 이메일 여부다(연락 이메일 seed의 릴레이 표시 — DOMAIN_MODEL §2.3 애플 특수).
 */
public sealed interface SocialIdentityOutcome {

    /**
     * 서명·발급자·수신자·만료 검증을 통과한 신원이다.
     */
    record Verified(String subject, @Nullable String email, boolean privateRelayEmail)
            implements SocialIdentityOutcome {}

    /**
     * 검증 실패다. {@code reason}은 로그용이며 클라이언트 응답에 노출하지 않는다.
     */
    record Failed(String reason) implements SocialIdentityOutcome {}
}
