package com.example.auth.external.notification;

import com.example.auth.domain.auth.port.NotificationChannel;
import com.example.auth.domain.auth.port.VerificationCodeSender;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 외부 호출 없는 Mock 인증코드 발송 어댑터다(dev/test 오프라인 E2E용).
 *
 * <p>대상별 최신 발송 코드를 인메모리로 보관해 테스트가 발송된 코드를 취득한다. 실 발송 벤더 어댑터는
 * 조달 후 이 포트를 교체 구현한다(실 어댑터는 코드를 로그에 남기지 않는다).
 */
@Component
public class MockVerificationCodeSender implements VerificationCodeSender {

    private static final Logger log = LoggerFactory.getLogger(MockVerificationCodeSender.class);

    private final Map<String, String> lastContentByTarget = new ConcurrentHashMap<>();

    @Override
    public void send(NotificationChannel channel, String target, String code) {
        lastContentByTarget.put(target, code);
        // dev 편의: Mock이라 콘텐츠(코드)를 노출한다. 실 어댑터는 이 로그를 남기지 않는다.
        log.info("[MOCK 인증코드] channel={} target={} code={}", channel, target, code);
    }

    /**
     * 대상에게 마지막으로 발송된 코드를 반환한다(dev/test 관측용).
     */
    public @Nullable String lastContent(String target) {
        return lastContentByTarget.get(target);
    }
}
