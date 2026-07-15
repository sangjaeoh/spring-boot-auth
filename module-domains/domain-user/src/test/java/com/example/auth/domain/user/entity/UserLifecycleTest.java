package com.example.auth.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UserLifecycleTest {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    @Test
    void withdrawPurgesPiiAndBumpsStatusVersion() {
        User user = activeUser();

        user.withdraw(NOW);

        assertThat(user.getStatus()).isEqualTo(LifecycleStatus.WITHDRAWN);
        assertThat(user.getStatusVersion()).isEqualTo(1);
        assertThat(user.getWithdrawnAt()).isEqualTo(NOW);
        assertThat(user.getProfile()).isNull();
        assertThat(user.getContact()).isNull();
        assertThat(user.getContactPhoneBidx()).isNull();
        assertThat(user.getCiHash()).isNull();
    }

    @Test
    void rejectsDoubleWithdrawal() {
        User user = activeUser();
        user.withdraw(NOW);

        assertThatThrownBy(() -> user.withdraw(NOW.plusSeconds(1)))
                .isInstanceOf(UserException.class)
                .satisfies(e ->
                        assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.USER_ALREADY_WITHDRAWN));
    }

    @Test
    void recordLoginIsMonotonicAndResetsDormancyNotice() {
        User user = activeUser();
        user.markDormancyNotified(NOW);

        user.recordLogin(NOW.plusSeconds(10));
        assertThat(user.getLastLoginAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(user.getDormancyNotifiedAt()).isNull();

        // 지연 도착한 과거 관측은 최근값을 되돌리지 않는다.
        user.recordLogin(NOW.plusSeconds(5));
        assertThat(user.getLastLoginAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void dormancyTransitionsGuardStatesAndBumpVersion() {
        User user = activeUser();

        user.makeDormant(NOW);
        assertThat(user.getStatus()).isEqualTo(LifecycleStatus.DORMANT);
        assertThat(user.getStatusVersion()).isEqualTo(1);
        assertThat(user.getDormantAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> user.makeDormant(NOW))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.INVALID_STATUS_TRANSITION));

        user.reactivate(NOW.plusSeconds(60));
        assertThat(user.getStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(user.getStatusVersion()).isEqualTo(2);
        assertThat(user.getDormantAt()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> user.reactivate(NOW))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.INVALID_STATUS_TRANSITION));
    }

    private static User activeUser() {
        Profile profile = Profile.of("홍길동", LocalDate.of(1990, 1, 1), Gender.MALE);
        Contact contact = Contact.of(Email.of("user@example.com"), PhoneNumber.of(Carrier.SKT, "+821012345678"));
        return User.create(profile, contact, "ci-hash", "bidx");
    }
}
