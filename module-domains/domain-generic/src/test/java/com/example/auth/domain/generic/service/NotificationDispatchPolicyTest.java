package com.example.auth.domain.generic.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.domain.generic.entity.NotificationCategory;
import com.example.auth.domain.generic.entity.NotificationChannel;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationDispatchPolicyTest {

    private final NotificationDispatchPolicy policy = new NotificationDispatchPolicy();

    @Test
    void dispatchesOnlyAllowedAndAvailableChannels() {
        Set<NotificationChannel> decided = policy.decide(
                NotificationCategory.ACCOUNT,
                Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH),
                Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS));

        assertThat(decided).containsExactly(NotificationChannel.EMAIL);
    }

    @Test
    void respectsOptOutForNonSecurityCategories() {
        Set<NotificationChannel> decided = policy.decide(
                NotificationCategory.MARKETING,
                Set.of(),
                Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.PUSH));

        assertThat(decided).isEmpty();
    }

    @Test
    void pushRequiresDeviceAvailability() {
        Set<NotificationChannel> decided = policy.decide(
                NotificationCategory.ACCOUNT,
                Set.of(NotificationChannel.PUSH),
                Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS));

        assertThat(decided).isEmpty();
    }

    @Test
    void securityIgnoresOptOutAndForcesEmail() {
        Set<NotificationChannel> decided = policy.decide(
                NotificationCategory.SECURITY,
                Set.of(),
                Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.PUSH));

        assertThat(decided).containsExactly(NotificationChannel.EMAIL);
    }

    @Test
    void securityFallsBackToSmsWhenEmailUnavailable() {
        Set<NotificationChannel> decided =
                policy.decide(NotificationCategory.SECURITY, Set.of(), Set.of(NotificationChannel.SMS));

        assertThat(decided).containsExactly(NotificationChannel.SMS);
    }

    @Test
    void securityDoesNotForceWhenContactChannelAlreadyAllowed() {
        Set<NotificationChannel> decided = policy.decide(
                NotificationCategory.SECURITY,
                Set.of(NotificationChannel.SMS, NotificationChannel.PUSH),
                Set.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.PUSH));

        assertThat(decided).containsExactlyInAnyOrder(NotificationChannel.SMS, NotificationChannel.PUSH);
    }

    @Test
    void returnsEmptyWhenNothingAvailable() {
        Set<NotificationChannel> decided = policy.decide(NotificationCategory.SECURITY, Set.of(), Set.of());

        assertThat(decided).isEmpty();
    }
}
