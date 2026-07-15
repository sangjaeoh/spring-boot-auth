package com.example.auth.domain.user.entity;

import com.example.auth.common.jpa.entity.BaseTimeEntity;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 알림 수신 설정 애그리거트 루트다(회원당 1개, 채널×카테고리 opt-in 매트릭스).
 *
 * <p>이 설정은 유저 소유 진실원본이고 실제 발송 판정은 제네릭 알림이 기기 권한과 AND 한다. SECURITY
 * 카테고리는 수신거부 불가 정책의 대상이다 — 연락 채널(EMAIL·SMS) 중 최소 1개 허용을 항상 유지한다.
 */
@Entity
@Table(schema = "usr", name = "notification_preference")
public class NotificationPreference extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToMany(mappedBy = "preference", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<NotificationPreferenceEntry> entries;

    protected NotificationPreference() {}

    private NotificationPreference(UUID userId, boolean marketingAgreed) {
        this.userId = userId;
        this.entries = new HashSet<>();
        for (NotificationChannel channel : NotificationChannel.values()) {
            for (NotificationCategory category : NotificationCategory.values()) {
                boolean allowed = category != NotificationCategory.MARKETING || marketingAgreed;
                this.entries.add(NotificationPreferenceEntry.create(this, channel, category, allowed));
            }
        }
    }

    /**
     * 전체 채널×카테고리 매트릭스로 수신 설정을 생성한다 — 비마케팅은 허용, 마케팅은 동의값을 따른다.
     */
    public static NotificationPreference create(UUID userId, boolean marketingAgreed) {
        return new NotificationPreference(userId, marketingAgreed);
    }

    /**
     * 해당 채널×카테고리 수신을 허용한다.
     */
    public void allow(NotificationChannel channel, NotificationCategory category) {
        entry(channel, category).changeAllowed(true);
    }

    /**
     * 해당 채널×카테고리 수신을 거부한다.
     *
     * @throws UserException SECURITY의 마지막 연락 채널(EMAIL·SMS)을 해제하려 하면(400)
     */
    public void disallow(NotificationChannel channel, NotificationCategory category) {
        if (category == NotificationCategory.SECURITY && isLastAllowedSecurityContactChannel(channel)) {
            throw new UserException(UserErrorCode.SECURITY_CHANNEL_REQUIRED);
        }
        entry(channel, category).changeAllowed(false);
    }

    /**
     * 마케팅 동의/철회를 매트릭스에 반영한다 — 채널 지정이 없으면 마케팅 전 채널에 적용한다.
     */
    public void syncMarketingFromConsent(boolean agreed, @Nullable NotificationChannel channel) {
        for (NotificationPreferenceEntry entry : entries) {
            if (entry.getCategory() == NotificationCategory.MARKETING
                    && (channel == null || entry.getChannel() == channel)) {
                entry.changeAllowed(agreed);
            }
        }
    }

    /**
     * 해당 채널×카테고리의 수신 허용 여부를 반환한다.
     */
    public boolean isAllowed(NotificationChannel channel, NotificationCategory category) {
        return entry(channel, category).isAllowed();
    }

    @Override
    public UUID getId() {
        return userId;
    }

    public UUID getUserId() {
        return userId;
    }

    private boolean isLastAllowedSecurityContactChannel(NotificationChannel channel) {
        if (channel == NotificationChannel.PUSH) {
            return false;
        }
        return entries.stream()
                .filter(entry -> entry.getCategory() == NotificationCategory.SECURITY)
                .filter(entry -> entry.getChannel() != NotificationChannel.PUSH)
                .filter(NotificationPreferenceEntry::isAllowed)
                .allMatch(entry -> entry.getChannel() == channel);
    }

    private NotificationPreferenceEntry entry(NotificationChannel channel, NotificationCategory category) {
        return entries.stream()
                .filter(e -> e.getChannel() == channel && e.getCategory() == category)
                .findFirst()
                // 생성 시 전체 매트릭스를 채우므로 부재는 데이터 훼손이다.
                .orElseThrow(() -> new IllegalStateException("수신 설정 엔트리가 없습니다: " + channel + "×" + category));
    }
}
