package com.example.auth.domain.auth.info;

import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.entity.RegistrationType;
import com.example.auth.domain.auth.port.SocialRegistrationContext;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 스텝셋 충족이 검증된 온보딩 완료 번들이다 — 유저에 보낼 단일 {@code CreateUser} 명령의 재료
 * (DOMAIN_MODEL §2.8 굵은 명령). {@code registrationId}가 멱등키다. {@code social}은 SOCIAL 가입의
 * {@code SocialConnection.connect} 재료다(LOCAL이면 null).
 */
public record RegistrationCompletionInfo(
        UUID registrationId,
        RegistrationType type,
        String loginEmail,
        UUID verificationRef,
        String ciHash,
        List<ConsentSelection> consents,
        @Nullable SocialRegistrationContext social) {

    public RegistrationCompletionInfo {
        consents = List.copyOf(consents);
    }
}
