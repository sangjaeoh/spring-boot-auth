package com.example.auth.domain.auth.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 소셜 연동 애그리거트 루트다. 한 소셜 계정({@code (provider, providerUserId)})은 하나의 인증계정에만
 * 연결되고, 한 계정은 provider당 1개만 연동한다 — 두 유니크는 DB 인덱스가 backstop한다.
 *
 * <p>{@code AuthenticationFactor} 패밀리로 {@code PasswordCredential}의 형제이며, 마지막 로그인 수단
 * 해제 거부(≥1 수단 유지)는 {@code LoginMethodPolicyValidator}가 강제한다.
 */
@Entity
@Table(schema = "auth", name = "social_connection")
public class SocialConnection extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    @Convert(converter = EmailConverter.class)
    @Column(name = "provider_email", length = 320)
    private @Nullable Email providerEmail;

    @Column(name = "is_private_relay")
    private boolean privateRelayEmail;

    @Column(name = "linked_at")
    private Instant linkedAt;

    protected SocialConnection() {}

    private SocialConnection(
            UUID id,
            UUID userId,
            SocialProvider provider,
            String providerUserId,
            @Nullable Email providerEmail,
            boolean privateRelayEmail,
            Instant linkedAt) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerEmail = providerEmail;
        this.privateRelayEmail = privateRelayEmail;
        this.linkedAt = linkedAt;
    }

    /**
     * 검증된 IdP 신원(subject·email)으로 소셜 연동을 생성한다.
     */
    public static SocialConnection create(
            UUID userId,
            SocialProvider provider,
            String providerUserId,
            @Nullable String providerEmail,
            boolean privateRelayEmail,
            Instant linkedAt) {
        if (providerUserId.isBlank()) {
            throw new IllegalArgumentException("providerUserId는 비어 있을 수 없습니다.");
        }
        return new SocialConnection(
                UuidV7Generator.generate(),
                userId,
                provider,
                providerUserId,
                providerEmail == null ? null : Email.of(providerEmail),
                privateRelayEmail,
                linkedAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public @Nullable Email getProviderEmail() {
        return providerEmail;
    }

    public boolean isPrivateRelayEmail() {
        return privateRelayEmail;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }
}
