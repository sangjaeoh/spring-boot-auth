package com.example.auth.domain.generic.entity;

/**
 * 알림 발송 채널이다.
 *
 * <p>유저의 수신설정·인증의 기기 푸시 권한은 각 도메인이 자기 어휘로 소유하고, 앱이 이 enum으로
 * 매핑해 발송 판정에 넘긴다(도메인 간 타입 공유 금지 — 각 도메인이 자기 채널 어휘를 소유한다).
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH
}
