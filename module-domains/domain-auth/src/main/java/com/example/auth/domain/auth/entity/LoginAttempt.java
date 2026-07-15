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
 * <p>연속 실패 잠금 카운터(Redis)와 분리한다. {@code riskScore}·{@code countryCode}는 성공 로그인의
 * 위험도 평가({@code RiskEvaluator})가 채우고, 실패 기록은 0·null이다.
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

    @Column(name = "country_code", length = 2)
    @Nullable
    private String countryCode;

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
            @Nullable String countryCode,
            Instant at) {
        this.id = id;
        this.userId = userId;
        this.result = result;
        this.failureReason = failureReason;
        this.ip = ip;
        this.deviceId = deviceId;
        this.riskScore = riskScore;
        this.countryCode = countryCode;
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
            @Nullable String countryCode,
            Instant at) {
        return new LoginAttempt(
                UuidV7Generator.generate(), userId, result, failureReason, ip, deviceId, riskScore, countryCode, at);
    }

    /**
     * IP를 가명화한다(뒤 절단 — IPv4는 앞 2옥텟, IPv6은 첫 그룹만 유지). append 사실·순서는 불변이고 PII
     * 값만 소거하는 보존 규칙의 유일한 수정 경로이며, 재적용해도 결과가 같다(멱등).
     */
    public void anonymizeIp() {
        this.ip = truncate(this.ip);
    }

    /**
     * IP가 이미 가명화되었는지 반환한다(가명화 스윕의 재처리 스킵 기준).
     */
    public boolean isIpAnonymized() {
        return ip.equals(truncate(ip));
    }

    private static String truncate(String ip) {
        if (ip.contains(".")) {
            String[] octets = ip.split("\\.", -1);
            return octets.length == 4 ? octets[0] + "." + octets[1] + ".*.*" : "*";
        }
        int firstGroupEnd = ip.indexOf(':');
        return firstGroupEnd > 0 ? ip.substring(0, firstGroupEnd) + ":*" : "*";
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

    public @Nullable String getCountryCode() {
        return countryCode;
    }

    public Instant getAt() {
        return at;
    }
}
