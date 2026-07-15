package com.example.auth.external.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.domain.auth.entity.SocialProvider;
import com.example.auth.domain.auth.port.SocialIdentityOutcome;
import org.junit.jupiter.api.Test;

class MockSocialIdentityProviderTest {

    private final MockSocialIdentityProvider provider = new MockSocialIdentityProvider();

    @Test
    void returnsSameIdentityForSameSubject() {
        SocialIdentityOutcome first = provider.verify(SocialProvider.KAKAO, "mock:kakao-1:user@example.com");
        SocialIdentityOutcome second = provider.verify(SocialProvider.KAKAO, "mock:kakao-1:user@example.com");

        assertThat(first)
                .isEqualTo(new SocialIdentityOutcome.Verified("kakao-1", "user@example.com", false))
                .isEqualTo(second);
    }

    @Test
    void verifiesTokenWithoutEmailAsNullEmail() {
        SocialIdentityOutcome outcome = provider.verify(SocialProvider.KAKAO, "mock:kakao-2");

        assertThat(outcome).isEqualTo(new SocialIdentityOutcome.Verified("kakao-2", null, false));
    }

    @Test
    void flagsAppleRelayDomainEmailAsPrivateRelay() {
        SocialIdentityOutcome outcome =
                provider.verify(SocialProvider.APPLE, "mock:apple-1:abc123@privaterelay.appleid.com");

        assertThat(outcome)
                .isEqualTo(new SocialIdentityOutcome.Verified("apple-1", "abc123@privaterelay.appleid.com", true));
    }

    @Test
    void failsOnMalformedToken() {
        assertThat(provider.verify(SocialProvider.GOOGLE, "not-a-mock-token"))
                .isInstanceOf(SocialIdentityOutcome.Failed.class);
        assertThat(provider.verify(SocialProvider.GOOGLE, "mock:")).isInstanceOf(SocialIdentityOutcome.Failed.class);
    }
}
