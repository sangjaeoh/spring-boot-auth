package com.example.auth.domain.user.info;

import com.example.auth.domain.user.entity.NotificationCategory;
import com.example.auth.domain.user.entity.NotificationChannel;

/**
 * 채널×카테고리 수신 허용 엔트리의 경계 조회 모델이다.
 */
public record NotificationPreferenceEntryInfo(
        NotificationChannel channel, NotificationCategory category, boolean allowed) {}
