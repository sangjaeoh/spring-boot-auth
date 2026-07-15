package com.example.auth.domain.auth.port;

/**
 * 사용자에게 알림 콘텐츠를 전달하는 벤더 중립 포트다.
 *
 * <p>인증 도메인이 소비하므로 도메인이 포트를 소유하고 external이 구현한다(docs/architecture.md — infra vs
 * external: 알림은 벤더 교체 대상 external). dev/test는 Mock 어댑터로 오프라인 검증한다. 실 발송·템플릿·재시도는
 * 후속 단계에서 도입한다.
 */
public interface NotificationSender {

    /**
     * 지정한 채널로 대상에게 콘텐츠를 발송한다.
     */
    void send(NotificationChannel channel, String target, String content);
}
