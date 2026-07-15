package com.example.auth.domain.auth.port;

/**
 * 알림 발송 채널이다({@link NotificationSender} 포트 계약).
 *
 * <p>EMAIL은 재설정·온보딩 코드, SMS는 온보딩 휴대폰 인증 코드 전달에 쓴다. PUSH는 소비처가 생기는
 * 후속 단계에서 추가한다.
 */
public enum NotificationChannel {
    EMAIL,
    SMS
}
