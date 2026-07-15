package com.example.auth.domain.auth.port;

import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.entity.RegistrationStep;
import com.example.auth.domain.auth.entity.RegistrationType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 온보딩 세션의 현재 상태 스냅샷이다({@link RegistrationSessionStore#find}).
 *
 * <p>PII 원문은 담지 않는다 — 본인인증 결과는 {@code verificationRef}(VerificationId)와 {@code ciHash}
 * (salted HMAC)만 참조한다. {@code emailChallengeId}/{@code phoneChallengeId}는 발급된 챌린지의 종별
 * 바인딩이다(교차 제출로 다른 스텝을 위조하지 못하게 verify 시 대조). {@code social}은 SOCIAL 세션의
 * 검증된 소셜 신원 컨텍스트다(LOCAL이면 null).
 */
public record RegistrationSnapshot(
        RegistrationType type,
        String tokenHash,
        String loginEmail,
        Set<RegistrationStep> completedSteps,
        @Nullable String emailChallengeId,
        @Nullable String phoneChallengeId,
        @Nullable UUID verificationRef,
        @Nullable String ciHash,
        List<ConsentSelection> consents,
        @Nullable SocialRegistrationContext social) {

    public RegistrationSnapshot {
        completedSteps = Set.copyOf(completedSteps);
        consents = List.copyOf(consents);
    }
}
