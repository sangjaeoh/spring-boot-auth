package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.user.entity.NotificationCategory;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.info.NotificationPreferenceInfo;
import java.util.List;

/**
 * 알림 수신 설정 매트릭스 응답이다.
 */
public record NotificationPreferenceResponse(List<Entry> entries) {

    public NotificationPreferenceResponse {
        entries = List.copyOf(entries);
    }

    public static NotificationPreferenceResponse from(NotificationPreferenceInfo info) {
        return new NotificationPreferenceResponse(info.entries().stream()
                .map(entry -> new Entry(entry.channel(), entry.category(), entry.allowed()))
                .toList());
    }

    public record Entry(NotificationChannel channel, NotificationCategory category, boolean allowed) {}
}
