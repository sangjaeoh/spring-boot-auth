package com.example.auth.domain.auth.port;

/**
 * 인증코드 발송 채널이다({@link VerificationCodeSender} 포트 계약).
 *
 * <p>EMAIL은 재설정·온보딩 코드, SMS는 온보딩 휴대폰 인증 코드 전달에 쓴다. 카테고리 알림 채널
 * (PUSH 포함)은 제네릭 도메인이 자기 어휘로 소유한다.
 */
public enum NotificationChannel {
    EMAIL,
    SMS
}
