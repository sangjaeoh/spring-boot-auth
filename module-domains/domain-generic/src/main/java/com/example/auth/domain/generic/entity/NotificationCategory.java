package com.example.auth.domain.generic.entity;

/**
 * 알림 카테고리다.
 *
 * <p>SECURITY는 수신거부를 무시하고 연락 채널(EMAIL·SMS) 최소 1개로 강제 발송한다(DOMAIN_MODEL §3.1 —
 * 계정탈취·유출 통지 의무).
 */
public enum NotificationCategory {
    SECURITY,
    ACCOUNT,
    MARKETING
}
