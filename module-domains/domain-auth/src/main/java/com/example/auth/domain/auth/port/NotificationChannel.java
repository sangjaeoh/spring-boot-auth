package com.example.auth.domain.auth.port;

/**
 * 알림 발송 채널이다({@link NotificationSender} 포트 계약).
 *
 * <p>이 슬라이스는 재설정 코드 전달에 EMAIL만 쓴다. SMS·PUSH는 소비처가 생기는 후속 단계에서 추가한다.
 */
public enum NotificationChannel {
    EMAIL
}
