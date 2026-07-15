package com.example.auth.external.social;

import com.example.auth.domain.auth.entity.SocialProvider;
import com.example.auth.domain.auth.port.SocialIdentityOutcome;
import com.example.auth.domain.auth.port.SocialIdentityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 외부 호출 없는 Mock 소셜 어댑터다(dev/test 오프라인 E2E용, {@code auth.social.mode=mock} 기본).
 *
 * <p>{@code id_token} 대신 {@code mock:{subject}} 또는 {@code mock:{subject}:{email}} 형식의 결정적
 * 토큰을 받는다 — 같은 subject의 재로그인은 같은 신원을 얻어 (provider, subject) 유니크 시맨틱이
 * dev에서도 실 IdP처럼 동작한다. 이메일이 {@code @privaterelay.appleid.com}으로 끝나면 애플 프라이빗
 * 릴레이로 표시한다. 실 4사 어댑터는 {@code auth.social.mode=oidc}로 전환한다.
 */
@Component
@ConditionalOnProperty(name = "auth.social.mode", havingValue = "mock", matchIfMissing = true)
public class MockSocialIdentityProvider implements SocialIdentityProvider {

    private static final String TOKEN_PREFIX = "mock";
    private static final String APPLE_RELAY_DOMAIN = "@privaterelay.appleid.com";

    @Override
    public SocialIdentityOutcome verify(SocialProvider provider, String idToken) {
        String[] parts = idToken.split(":", 3);
        if (!TOKEN_PREFIX.equals(parts[0]) || parts.length < 2 || parts[1].isBlank()) {
            return new SocialIdentityOutcome.Failed("mock 토큰 형식 불일치");
        }
        String subject = parts[1];
        String email = parts.length == 3 && !parts[2].isBlank() ? parts[2] : null;
        boolean privateRelay = email != null && email.endsWith(APPLE_RELAY_DOMAIN);
        return new SocialIdentityOutcome.Verified(subject, email, privateRelay);
    }
}
