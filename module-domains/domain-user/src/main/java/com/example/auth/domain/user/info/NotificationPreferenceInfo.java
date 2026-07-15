package com.example.auth.domain.user.info;

import com.example.auth.domain.user.entity.NotificationCategory;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.NotificationPreference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 알림 수신 설정 매트릭스의 경계 조회 모델이다(전 채널×카테고리 엔트리 포함).
 */
public record NotificationPreferenceInfo(UUID userId, List<NotificationPreferenceEntryInfo> entries) {

    public NotificationPreferenceInfo {
        entries = List.copyOf(entries);
    }

    public static NotificationPreferenceInfo from(NotificationPreference preference) {
        List<NotificationPreferenceEntryInfo> entries = new ArrayList<>();
        for (NotificationChannel channel : NotificationChannel.values()) {
            for (NotificationCategory category : NotificationCategory.values()) {
                entries.add(new NotificationPreferenceEntryInfo(
                        channel, category, preference.isAllowed(channel, category)));
            }
        }
        return new NotificationPreferenceInfo(preference.getUserId(), entries);
    }

    /**
     * 해당 카테고리에서 수신 허용된 채널 집합을 반환한다.
     */
    public Set<NotificationChannel> allowedChannels(NotificationCategory category) {
        EnumSet<NotificationChannel> allowed = EnumSet.noneOf(NotificationChannel.class);
        for (NotificationPreferenceEntryInfo entry : entries) {
            if (entry.category() == category && entry.allowed()) {
                allowed.add(entry.channel());
            }
        }
        return allowed;
    }
}
