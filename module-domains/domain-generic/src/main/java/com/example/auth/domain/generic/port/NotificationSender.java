package com.example.auth.domain.generic.port;

import com.example.auth.domain.generic.entity.NotificationChannel;

/**
 * 카테고리 알림을 수신자에게 전달하는 벤더 중립 포트다(EMAIL/SMS/PUSH — DOMAIN_MODEL §3.1).
 *
 * <p>제네릭 도메인이 소비하므로 이 도메인이 포트를 소유하고 external이 구현한다(docs/architecture.md —
 * 알림은 벤더 교체 대상 external). 템플릿 렌더링은 어댑터 소관이다 — 도메인은 템플릿 식별자와 렌더
 * 데이터(JSON)만 넘긴다. dev/test는 Mock 어댑터로 오프라인 검증한다.
 */
public interface NotificationSender {

    /**
     * 지정한 채널로 수신 대상에게 알림을 발송한다.
     *
     * @param target 채널별 수신 주소(EMAIL=이메일, SMS=E.164 전화번호, PUSH=푸시 토큰)
     * @throws RuntimeException 발송 실패 시(호출자가 실패 이력·재시도로 흡수한다)
     */
    void send(NotificationChannel channel, String target, String templateId, String payload);
}
