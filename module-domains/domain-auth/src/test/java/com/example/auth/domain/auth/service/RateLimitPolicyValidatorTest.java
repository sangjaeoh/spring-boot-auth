package com.example.auth.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.crypto.TokenHasher;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.NotificationChannel;
import com.example.auth.domain.auth.port.RateLimitStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RateLimitPolicyValidatorTest {

    private final InMemoryRateLimitStore store = new InMemoryRateLimitStore();
    private final TokenHasher identityHasher = raw -> raw;

    private RateLimitPolicyValidator validator(int loginIpLimit, long cooldownSeconds, int dailyEmail, int dailySms) {
        return new RateLimitPolicyValidator(
                store, identityHasher, loginIpLimit, 10, 60, 5, 900, cooldownSeconds, dailyEmail, dailySms);
    }

    @Test
    void allowsLoginWithinLimitAndRejectsBeyond() {
        RateLimitPolicyValidator validator = validator(3, 0, 10, 5);

        for (int i = 0; i < 3; i++) {
            assertThatCode(() -> validator.checkLogin("1.2.3.4", "user@example.com"))
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> validator.checkLogin("1.2.3.4", "user@example.com"))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.RATE_LIMITED);
    }

    @Test
    void limitsAreKeyedPerIp() {
        RateLimitPolicyValidator validator = validator(1, 0, 10, 5);

        validator.checkLogin("1.1.1.1", null);
        assertThatCode(() -> validator.checkLogin("2.2.2.2", null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsCodeIssueDuringResendCooldown() {
        RateLimitPolicyValidator validator = validator(10, 60, 10, 5);

        validator.checkCodeIssue(NotificationChannel.EMAIL, "user@example.com");

        assertThatThrownBy(() -> validator.checkCodeIssue(NotificationChannel.EMAIL, "user@example.com"))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.RATE_LIMITED);
    }

    @Test
    void rejectsCodeIssueBeyondChannelDailyLimit() {
        RateLimitPolicyValidator validator = validator(10, 0, 2, 5);

        validator.checkCodeIssue(NotificationChannel.EMAIL, "user@example.com");
        validator.checkCodeIssue(NotificationChannel.EMAIL, "user@example.com");

        assertThatThrownBy(() -> validator.checkCodeIssue(NotificationChannel.EMAIL, "user@example.com"))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.RATE_LIMITED);
        // 채널이 다르면 별도 한도다.
        assertThatCode(() -> validator.checkCodeIssue(NotificationChannel.SMS, "user@example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void appliesPasswordResetLimitUniformlyPerTarget() {
        RateLimitPolicyValidator validator = validator(10, 0, 10, 5);

        for (int i = 0; i < 5; i++) {
            validator.checkPasswordResetInitiate("9.9.9." + i, "victim@example.com");
        }
        assertThatThrownBy(() -> validator.checkPasswordResetInitiate("9.9.9.100", "victim@example.com"))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.RATE_LIMITED);
    }

    private static class InMemoryRateLimitStore implements RateLimitStore {

        private final Map<String, Long> counters = new HashMap<>();
        private final Set<String> cooldowns = new HashSet<>();

        @Override
        public long increment(String key, Duration window) {
            return counters.merge(key, 1L, Long::sum);
        }

        @Override
        public boolean tryAcquire(String key, Duration cooldown) {
            return cooldowns.add(key);
        }
    }
}
