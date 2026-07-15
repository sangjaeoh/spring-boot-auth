package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.entity.SocialConnection;
import com.example.auth.domain.auth.entity.SocialProvider;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.SocialConnectionRepository;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 연동 생성을 담당한다. 유니크 불변식은 읽기 판정으로 409를 만들고, 경합 이중연결은 DB 유니크
 * 인덱스가 backstop한다.
 */
@Service
public class SocialConnectionAppender {

    private final SocialConnectionRepository repository;

    public SocialConnectionAppender(SocialConnectionRepository repository) {
        this.repository = repository;
    }

    /**
     * 검증된 IdP 신원을 계정에 연동한다.
     *
     * @throws AuthException 소셜 계정이 이미 타 계정에 연결됐으면(409), 이 계정에 같은 provider가 이미
     *     연동됐으면(409)
     */
    @Transactional
    public void connect(
            UUID userId,
            SocialProvider provider,
            String providerUserId,
            @Nullable String providerEmail,
            boolean privateRelayEmail,
            Instant now) {
        if (repository.existsByProviderAndProviderUserId(provider, providerUserId)) {
            throw new AuthException(AuthErrorCode.SOCIAL_ALREADY_CONNECTED);
        }
        if (repository.existsByUserIdAndProvider(userId, provider)) {
            throw new AuthException(AuthErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
        }
        repository.save(
                SocialConnection.create(userId, provider, providerUserId, providerEmail, privateRelayEmail, now));
    }
}
