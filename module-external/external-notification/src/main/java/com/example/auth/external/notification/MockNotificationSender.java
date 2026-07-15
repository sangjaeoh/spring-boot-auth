package com.example.auth.external.notification;

import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.domain.generic.port.NotificationSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 외부 호출 없는 Mock 카테고리 알림 어댑터다(dev/test 오프라인 E2E용,
 * {@code generic.notification.mode=mock} 기본).
 *
 * <p>발송 기록을 인메모리로 보관해 테스트가 발송 사실·채널·템플릿을 검증한다. 실 벤더 어댑터
 * ({@code VendorNotificationSender})는 조달 후 설정 스위치로 교체한다.
 */
@Component
@ConditionalOnProperty(name = "generic.notification.mode", havingValue = "mock", matchIfMissing = true)
public class MockNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationSender.class);

    private final List<SentNotification> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(NotificationChannel channel, String target, String templateId, String payload) {
        sent.add(new SentNotification(channel, target, templateId, payload));
        // dev 편의: Mock이라 페이로드를 노출한다. 실 어댑터는 이 로그를 남기지 않는다.
        log.info("[MOCK 알림] channel={} target={} templateId={} payload={}", channel, target, templateId, payload);
    }

    /**
     * 대상에게 발송된 알림 기록을 발송 순으로 반환한다(dev/test 관측용).
     */
    public List<SentNotification> sentTo(String target) {
        return sent.stream().filter(s -> s.target().equals(target)).toList();
    }

    /**
     * 전체 발송 기록을 발송 순으로 반환한다(dev/test 관측용).
     */
    public List<SentNotification> all() {
        return List.copyOf(sent);
    }

    /**
     * 발송 기록 한 건이다(dev/test 관측용).
     */
    public record SentNotification(NotificationChannel channel, String target, String templateId, String payload) {}
}
