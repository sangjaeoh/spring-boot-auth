package com.example.auth.domain.auth.port;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 원타임 인증코드 검증의 원자 판정 결과다({@link VerificationChallengeStore#verify}).
 *
 * <p>{@code VERIFIED}일 때만 챌린지에 바인딩된 {@code subjectId}(대상 userId)를 담는다. 그 외는 모두
 * {@code null}이며 소비처는 열거 저항을 위해 단일 실패로 통일한다.
 */
public record VerificationResult(Status status, @Nullable UUID subjectId) {

    public enum Status {
        VERIFIED,
        MISMATCH,
        TOO_MANY_ATTEMPTS,
        NOT_FOUND
    }

    public static VerificationResult verified(UUID subjectId) {
        return new VerificationResult(Status.VERIFIED, subjectId);
    }

    public static VerificationResult mismatch() {
        return new VerificationResult(Status.MISMATCH, null);
    }

    public static VerificationResult tooManyAttempts() {
        return new VerificationResult(Status.TOO_MANY_ATTEMPTS, null);
    }

    public static VerificationResult notFound() {
        return new VerificationResult(Status.NOT_FOUND, null);
    }

    public boolean isVerified() {
        return status == Status.VERIFIED;
    }
}
