package com.example.auth.domain.auth.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 디바이스 애그리거트 루트다. 로그인 기기를 {@code (userId, fingerprint)}로 재식별하고 푸시
 * 타겟(토큰·OS 권한)을 소유한다(DOMAIN_MODEL §2.5). 유니크는 DB 인덱스가 backstop한다.
 *
 * <p>삭제는 물리 삭제다 — 같은 기기의 재로그인이 지문 유니크와 충돌하지 않고 신규 기기로 재등록되게
 * 한다(재등록 시 신규 기기 감지가 다시 발동하는 것이 보안상 의도된 거동이다).
 */
@Entity
@Table(schema = "auth", name = "device")
public class Device extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 20)
    private DevicePlatform platform;

    @Column(name = "fingerprint", length = 255)
    private String fingerprint;

    @Column(name = "last_ip", length = 45)
    private @Nullable String lastIp;

    @Column(name = "last_accessed_at")
    private @Nullable Instant lastAccessedAt;

    @Column(name = "trusted")
    private boolean trusted;

    @Column(name = "push_token", length = 512)
    private @Nullable String pushToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_platform", length = 20)
    private @Nullable PushPlatform pushPlatform;

    @Column(name = "push_enabled")
    private boolean pushEnabled;

    protected Device() {}

    private Device(UUID id, UUID userId, String deviceName, DevicePlatform platform, String fingerprint) {
        this.id = id;
        this.userId = userId;
        this.deviceName = deviceName;
        this.platform = platform;
        this.fingerprint = fingerprint;
        this.trusted = false;
        this.pushEnabled = false;
    }

    /**
     * 미신뢰·푸시 미허용 상태의 새 기기를 생성한다.
     */
    public static Device create(UUID userId, String deviceName, DevicePlatform platform, String fingerprint) {
        if (fingerprint.isBlank()) {
            throw new IllegalArgumentException("fingerprint는 비어 있을 수 없습니다.");
        }
        if (deviceName.isBlank()) {
            throw new IllegalArgumentException("deviceName은 비어 있을 수 없습니다.");
        }
        return new Device(UuidV7Generator.generate(), userId, deviceName, platform, fingerprint);
    }

    /**
     * 접속 사실(IP·시각)을 기록한다.
     */
    public void recordAccess(String ip, Instant at) {
        this.lastIp = ip;
        this.lastAccessedAt = at;
    }

    /**
     * 기기를 신뢰로 표시한다.
     */
    public void trust() {
        this.trusted = true;
    }

    /**
     * 기기 신뢰를 해제한다.
     */
    public void untrust() {
        this.trusted = false;
    }

    /**
     * 푸시 토큰과 전송 플랫폼을 등록한다(재등록은 교체).
     */
    public void registerPushToken(String token, PushPlatform platform) {
        if (token.isBlank()) {
            throw new IllegalArgumentException("pushToken은 비어 있을 수 없습니다.");
        }
        this.pushToken = token;
        this.pushPlatform = platform;
    }

    /**
     * OS 푸시 권한 상태를 반영한다.
     */
    public void reflectPushPermission(boolean enabled) {
        this.pushEnabled = enabled;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public DevicePlatform getPlatform() {
        return platform;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public @Nullable String getLastIp() {
        return lastIp;
    }

    public @Nullable Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public boolean isTrusted() {
        return trusted;
    }

    public @Nullable String getPushToken() {
        return pushToken;
    }

    public @Nullable PushPlatform getPushPlatform() {
        return pushPlatform;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }
}
