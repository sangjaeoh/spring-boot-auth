package com.example.auth.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.domain.user.exception.UserException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 본인인증 상태 전이 가드를 검증한다(REQUESTED에서만 complete/fail 허용, 완료 후 결과 불변).
 */
class IdentityVerificationTest {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    @Test
    void createsAsRequested() {
        IdentityVerification verification = IdentityVerification.create(Provider.MOCK, NOW, NOW.plusSeconds(600));

        assertThat(verification.getStatus()).isEqualTo(VerificationStatus.REQUESTED);
        assertThat(verification.getResult()).isNull();
        assertThat(verification.getUserId()).isNull();
    }

    @Test
    void completeTransitionsToVerifiedWithResult() {
        IdentityVerification verification = IdentityVerification.create(Provider.MOCK, NOW, NOW.plusSeconds(600));

        verification.complete(result(), NOW.plusSeconds(10));

        assertThat(verification.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(verification.getResult()).isNotNull();
        assertThat(verification.getVerifiedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void completeIsRejectedAfterTerminalState() {
        IdentityVerification verification = IdentityVerification.create(Provider.MOCK, NOW, NOW.plusSeconds(600));
        verification.complete(result(), NOW);

        assertThatThrownBy(() -> verification.complete(result(), NOW)).isInstanceOf(UserException.class);
        assertThatThrownBy(verification::fail).isInstanceOf(UserException.class);
    }

    @Test
    void failTransitionsToFailed() {
        IdentityVerification verification = IdentityVerification.create(Provider.MOCK, NOW, NOW.plusSeconds(600));

        verification.fail();

        assertThat(verification.getStatus()).isEqualTo(VerificationStatus.FAILED);
        assertThatThrownBy(() -> verification.complete(result(), NOW)).isInstanceOf(UserException.class);
    }

    private static VerificationResult result() {
        return VerificationResult.of(
                "홍길동", LocalDate.of(1990, 3, 14), Gender.MALE, Carrier.SKT, "+821012345678", "1:cihash", "di-raw");
    }
}
