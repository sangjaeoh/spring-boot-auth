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
 * 로그인 성공/실패의 불변 시도 기록이다(append-only 루트).
 *
 * <p>연속 실패 잠금 카운터(Redis)와 분리한다. 위험도·기기는 후속 단계에서 채우며 이 슬라이스는 플레이스홀더
 * ({@code riskScore=0}, {@code deviceId=null})다.
 */
@Entity
@Table(schema = "auth", name = "login_attempt")
public class LoginAttempt extends BaseTimeEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "user_id")
    @Nullable
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20)
    private LoginResult result;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    @Nullable
    private FailureReason failureReason;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "device_id")
    @Nullable
    private UUID deviceId;

    @Column(name = "risk_score")
    private int riskScore;

    @Column(name = "attempted_at")
    private Instant at;

    protected LoginAttempt() {}

    private LoginAttempt(
            UUID id,
            @Nullable UUID userId,
            LoginResult result,
            @Nullable FailureReason failureReason,
            String ip,
            @Nullable UUID deviceId,
            int riskScore,
            Instant at) {
        this.id = id;
        this.userId = userId;
        this.result = result;
        this.failureReason = failureReason;
        this.ip = ip;
        this.deviceId = deviceId;
        this.riskScore = riskScore;
        this.at = at;
    }

    /**
     * 새 로그인 시도 기록을 생성한다.
     */
    public static LoginAttempt create(
            @Nullable UUID userId,
            LoginResult result,
            @Nullable FailureReason failureReason,
            String ip,
            @Nullable UUID deviceId,
            int riskScore,
            Instant at) {
        return new LoginAttempt(UuidV7Generator.generate(), userId, result, failureReason, ip, deviceId, riskScore, at);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public @Nullable UUID getUserId() {
        return userId;
    }

    public LoginResult getResult() {
        return result;
    }

    public @Nullable FailureReason getFailureReason() {
        return failureReason;
    }

    public String getIp() {
        return ip;
    }

    public @Nullable UUID getDeviceId() {
        return deviceId;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public Instant getAt() {
        return at;
    }
}
