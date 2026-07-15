package com.example.auth.app.api.facade;

import com.example.auth.domain.auth.info.PushTargetInfo;
import com.example.auth.domain.auth.service.DeviceReader;
import com.example.auth.domain.generic.entity.NotificationCategory;
import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.domain.generic.entity.NotificationStatus;
import com.example.auth.domain.generic.info.NotificationInfo;
import com.example.auth.domain.generic.service.NotificationDispatchPolicy;
import com.example.auth.domain.generic.service.NotificationProcessor;
import com.example.auth.domain.user.info.NotificationRecipientInfo;
import com.example.auth.domain.user.service.NotificationPreferenceReader;
import com.example.auth.domain.user.service.UserReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 크로스 도메인 사실을 모아 알림 발송을 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지
 * 않는다): 수신설정(유저) AND 수신 주소(유저 contact) AND 기기 푸시 권한(인증)을 채널 집합으로 환원해
 * 제네릭 발송 판정·기록에 넘긴다.
 *
 * <p>수신자 주소는 유저 {@code contactEmail}/{@code contactPhone}만 쓴다(인증 {@code loginEmail} 미사용).
 * 수신자·가용 채널이 없으면 발송을 스킵한다(예: 탈퇴로 PII가 파기된 회원).
 */
@Component
public class NotificationFacade {

    private static final Logger log = LoggerFactory.getLogger(NotificationFacade.class);

    // 이벤트 페이로드 코덱과 동일하게 웹 계층 ObjectMapper 설정과 결합하지 않는 전용 매퍼를 쓴다.
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UserReader userReader;
    private final NotificationPreferenceReader notificationPreferenceReader;
    private final DeviceReader deviceReader;
    private final NotificationDispatchPolicy notificationDispatchPolicy;
    private final NotificationProcessor notificationProcessor;

    public NotificationFacade(
            UserReader userReader,
            NotificationPreferenceReader notificationPreferenceReader,
            DeviceReader deviceReader,
            NotificationDispatchPolicy notificationDispatchPolicy,
            NotificationProcessor notificationProcessor) {
        this.userReader = userReader;
        this.notificationPreferenceReader = notificationPreferenceReader;
        this.deviceReader = deviceReader;
        this.notificationDispatchPolicy = notificationDispatchPolicy;
        this.notificationProcessor = notificationProcessor;
    }

    /**
     * 보안(SECURITY) 알림을 발송한다 — opt-out을 무시하고 연락 채널 최소 1개로 강제된다.
     *
     * @throws IllegalStateException 어느 채널이든 발송 실패 시(소비자가 DLQ 재시도로 수렴)
     */
    public void dispatchSecurity(UUID userId, String templateId, Map<String, ?> payload, UUID sourceEventId) {
        dispatch(userId, NotificationCategory.SECURITY, templateId, payload, sourceEventId);
    }

    private void dispatch(
            UUID userId, NotificationCategory category, String templateId, Map<String, ?> payload, UUID sourceEventId) {
        Optional<NotificationRecipientInfo> recipient = userReader.findNotificationRecipient(userId);
        List<PushTargetInfo> pushTargets = deviceReader.findPushTargets(userId);
        Set<NotificationChannel> available = EnumSet.noneOf(NotificationChannel.class);
        if (recipient.isPresent()) {
            available.add(NotificationChannel.EMAIL);
            available.add(NotificationChannel.SMS);
        }
        if (!pushTargets.isEmpty()) {
            available.add(NotificationChannel.PUSH);
        }
        Set<NotificationChannel> decided =
                notificationDispatchPolicy.decide(category, allowedChannels(userId, category), available);
        if (decided.isEmpty()) {
            log.info("알림 발송 스킵(가용·허용 채널 없음): userId={} templateId={}", userId, templateId);
            return;
        }
        String payloadJson = JSON.writeValueAsString(payload);
        Instant now = Instant.now();
        List<NotificationChannel> failed = new ArrayList<>();
        for (NotificationChannel channel : decided) {
            String target =
                    switch (channel) {
                        case EMAIL -> recipient.orElseThrow().contactEmail();
                        case SMS -> recipient.orElseThrow().contactPhone();
                        // (sourceEventId, channel) 멱등 키가 채널당 1행이라 푸시는 최근 접속 기기 1대로 보낸다.
                        case PUSH -> pushTargets.getFirst().pushToken();
                    };
            NotificationInfo info = notificationProcessor.dispatch(
                    userId, category, channel, templateId, payloadJson, target, sourceEventId, now);
            if (info.status() == NotificationStatus.FAILED) {
                failed.add(channel);
            }
        }
        if (!failed.isEmpty()) {
            throw new IllegalStateException("알림 발송 실패(재시도 대상): channels=" + failed + " template=" + templateId);
        }
    }

    private Set<NotificationChannel> allowedChannels(UUID userId, NotificationCategory category) {
        var userCategory = com.example.auth.domain.user.entity.NotificationCategory.valueOf(category.name());
        return notificationPreferenceReader
                .find(userId)
                .map(info -> mapChannels(info.allowedChannels(userCategory)))
                .orElseGet(() -> defaultAllowed(category));
    }

    private Set<NotificationChannel> mapChannels(
            Set<com.example.auth.domain.user.entity.NotificationChannel> channels) {
        EnumSet<NotificationChannel> mapped = EnumSet.noneOf(NotificationChannel.class);
        for (var channel : channels) {
            mapped.add(NotificationChannel.valueOf(channel.name()));
        }
        return mapped;
    }

    private Set<NotificationChannel> defaultAllowed(NotificationCategory category) {
        // 설정 미존재 기본은 생성 기본값과 정합: 비마케팅 허용, 마케팅 거부.
        return category == NotificationCategory.MARKETING ? Set.of() : EnumSet.allOf(NotificationChannel.class);
    }
}
