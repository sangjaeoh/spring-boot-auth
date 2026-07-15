package com.example.auth.domain.user.entity;

/**
 * 알림 카테고리다. SECURITY는 수신거부 불가 정책의 대상이다(최소 연락 채널 유지 강제).
 */
public enum NotificationCategory {
    SECURITY,
    ACCOUNT,
    MARKETING
}
