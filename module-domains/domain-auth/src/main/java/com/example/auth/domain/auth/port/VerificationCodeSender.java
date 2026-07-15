package com.example.auth.domain.auth.port;

/**
 * 원타임 인증코드 원문을 대상에게 전달하는 벤더 중립 포트다.
 *
 * <p>인증 도메인이 소비하므로 도메인이 포트를 소유하고 external이 구현한다(docs/architecture.md — infra vs
 * external: 알림은 벤더 교체 대상 external). 카테고리 알림(수신설정 판정·발송 이력)은 제네릭 도메인의
 * {@code NotificationSender}가 소유하며, 이 포트는 주어진 target으로 코드를 즉시 전달하는 별개 계약이다 —
 * 도메인 간 의존 금지로 포트를 공유하지 않는다. dev/test는 Mock 어댑터로 오프라인 검증한다.
 */
public interface VerificationCodeSender {

    /**
     * 지정한 채널로 대상에게 인증코드를 발송한다.
     */
    void send(NotificationChannel channel, String target, String code);
}
