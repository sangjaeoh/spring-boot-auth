package com.example.auth.external.notification;

import com.example.auth.domain.auth.port.NotificationChannel;
import com.example.auth.domain.auth.port.NotificationSender;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 외부 호출 없는 Mock 알림 어댑터다(dev/test 오프라인 E2E용).
 *
 * <p>대상별 최신 발송 콘텐츠를 인메모리로 보관해 테스트가 발송된 코드를 취득한다. 실 발송·템플릿·재시도를
 * 갖춘 벤더 어댑터는 P5에서 이 포트를 교체 구현한다(실 어댑터는 콘텐츠를 로그에 남기지 않는다).
 */
@Component
public class MockNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(MockNotificationSender.class);

    private final Map<String, String> lastContentByTarget = new ConcurrentHashMap<>();

    @Override
    public void send(NotificationChannel channel, String target, String content) {
        lastContentByTarget.put(target, content);
        // dev 편의: Mock이라 콘텐츠(코드)를 노출한다. 실 어댑터는 이 로그를 남기지 않는다.
        log.info("[MOCK 알림] channel={} target={} content={}", channel, target, content);
    }

    /**
     * 대상에게 마지막으로 발송된 콘텐츠를 반환한다(dev/test 관측용).
     */
    public @Nullable String lastContent(String target) {
        return lastContentByTarget.get(target);
    }
}
