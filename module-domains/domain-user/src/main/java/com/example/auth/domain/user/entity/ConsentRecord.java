package com.example.auth.domain.user.entity;

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
 * 동의 이력 애그리거트 루트다 — append-only 불변 로그(update/delete 없음, 법적 입증 근거).
 *
 * <p>가입 최초 동의는 온보딩 버퍼에 있다가 {@code CreateUser} 트랜잭션에서 {@code userId}와 함께
 * append된다(userId 없는 선-기록 금지). 현재 동의 스냅샷은 {@code ConsentState} fold 읽기모델이 소유한다.
 */
@Entity
@Table(schema = "usr", name = "consent_record")
public class ConsentRecord extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "terms_type", length = 30)
    private TermsType termsType;

    @Column(name = "terms_version")
    private int termsVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 10)
    private ConsentAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 10)
    @Nullable
    private NotificationChannel channel;

    @Column(name = "at")
    private Instant at;

    protected ConsentRecord() {}

    private ConsentRecord(
            UUID id,
            UUID userId,
            TermsType termsType,
            int termsVersion,
            ConsentAction action,
            @Nullable NotificationChannel channel,
            Instant at) {
        this.id = id;
        this.userId = userId;
        this.termsType = termsType;
        this.termsVersion = termsVersion;
        this.action = action;
        this.channel = channel;
        this.at = at;
    }

    /**
     * 동의/철회 이력 한 건을 생성한다(생성 후 불변 — 정정도 새 append로만 표현한다). 채널은 마케팅 동의의
     * 채널 한정 선택에만 쓴다(그 외 null).
     */
    public static ConsentRecord create(
            UUID userId,
            TermsType termsType,
            int termsVersion,
            ConsentAction action,
            @Nullable NotificationChannel channel,
            Instant at) {
        return new ConsentRecord(UuidV7Generator.generate(), userId, termsType, termsVersion, action, channel, at);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public TermsType getTermsType() {
        return termsType;
    }

    public int getTermsVersion() {
        return termsVersion;
    }

    public ConsentAction getAction() {
        return action;
    }

    public @Nullable NotificationChannel getChannel() {
        return channel;
    }

    public Instant getAt() {
        return at;
    }
}
