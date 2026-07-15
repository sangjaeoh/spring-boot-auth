package com.example.auth.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPreferenceTest {

    @Test
    void createsFullMatrixWithMarketingFromConsent() {
        NotificationPreference preference = NotificationPreference.create(UUID.randomUUID(), false);

        for (NotificationChannel channel : NotificationChannel.values()) {
            assertThat(preference.isAllowed(channel, NotificationCategory.SECURITY))
                    .isTrue();
            assertThat(preference.isAllowed(channel, NotificationCategory.ACCOUNT))
                    .isTrue();
            assertThat(preference.isAllowed(channel, NotificationCategory.MARKETING))
                    .isFalse();
        }
    }

    @Test
    void rejectsDisallowingLastSecurityContactChannel() {
        NotificationPreference preference = NotificationPreference.create(UUID.randomUUID(), false);
        preference.disallow(NotificationChannel.SMS, NotificationCategory.SECURITY);

        assertThatThrownBy(() -> preference.disallow(NotificationChannel.EMAIL, NotificationCategory.SECURITY))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.SECURITY_CHANNEL_REQUIRED));
    }

    @Test
    void allowsDisallowingSecurityChannelWhileAnotherContactChannelRemains() {
        NotificationPreference preference = NotificationPreference.create(UUID.randomUUID(), false);

        assertThatCode(() -> preference.disallow(NotificationChannel.EMAIL, NotificationCategory.SECURITY))
                .doesNotThrowAnyException();
        assertThat(preference.isAllowed(NotificationChannel.SMS, NotificationCategory.SECURITY))
                .isTrue();
    }

    @Test
    void securityPushIsNotAContactChannelAndCanAlwaysBeDisallowed() {
        NotificationPreference preference = NotificationPreference.create(UUID.randomUUID(), false);
        preference.disallow(NotificationChannel.SMS, NotificationCategory.SECURITY);

        assertThatCode(() -> preference.disallow(NotificationChannel.PUSH, NotificationCategory.SECURITY))
                .doesNotThrowAnyException();
    }

    @Test
    void syncsMarketingForSingleChannelOrAllChannels() {
        NotificationPreference preference = NotificationPreference.create(UUID.randomUUID(), false);

        preference.syncMarketingFromConsent(true, NotificationChannel.EMAIL);
        assertThat(preference.isAllowed(NotificationChannel.EMAIL, NotificationCategory.MARKETING))
                .isTrue();
        assertThat(preference.isAllowed(NotificationChannel.SMS, NotificationCategory.MARKETING))
                .isFalse();

        preference.syncMarketingFromConsent(true, null);
        assertThat(preference.isAllowed(NotificationChannel.SMS, NotificationCategory.MARKETING))
                .isTrue();
        assertThat(preference.isAllowed(NotificationChannel.PUSH, NotificationCategory.MARKETING))
                .isTrue();

        preference.syncMarketingFromConsent(false, null);
        for (NotificationChannel channel : NotificationChannel.values()) {
            assertThat(preference.isAllowed(channel, NotificationCategory.MARKETING))
                    .isFalse();
        }
    }
}
