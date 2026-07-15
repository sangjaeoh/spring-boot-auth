package com.example.auth.domain.auth.repository;

import com.example.auth.domain.auth.entity.SocialConnection;
import com.example.auth.domain.auth.entity.SocialProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 소셜 연동의 영속 포트다(Spring Data 인터페이스 = 도메인 포트).
 *
 * <p>소프트삭제 대상이 아니므로 base finder를 그대로 쓴다. 해제는 물리 삭제다(자격증명 폐기 —
 * DOMAIN_MODEL §2.3 disconnect).
 */
public interface SocialConnectionRepository extends JpaRepository<SocialConnection, UUID> {

    Optional<SocialConnection> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    Optional<SocialConnection> findByUserIdAndProvider(UUID userId, SocialProvider provider);

    boolean existsByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    boolean existsByUserIdAndProvider(UUID userId, SocialProvider provider);

    long countByUserId(UUID userId);
}
