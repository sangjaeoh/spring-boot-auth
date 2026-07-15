package com.example.auth.domain.generic.service;

import com.example.auth.domain.generic.entity.NotificationCategory;
import com.example.auth.domain.generic.entity.NotificationChannel;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 발송 채널 판정이다: 발송 = 수신설정(유저) AND 채널 가용성(수신 주소·기기 푸시 권한, 인증) AND
 * 카테고리 정책(DOMAIN_MODEL §3.1).
 *
 * <p>크로스 도메인 사실(수신설정·연락처·기기 권한)은 앱이 채널 집합으로 환원해 넘긴다 — 도메인 간
 * 타입 의존 없이 판정 규칙만 이 도메인이 소유한다. 이름은 DOMAIN_MODEL 도메인 서비스 표의 명명을
 * 따른다({@code Policy} 접미사 예외).
 */
@Service
public class NotificationDispatchPolicy {

    /**
     * 발송할 채널 집합을 판정한다 — 기본은 수신 허용과 가용의 교집합이고, SECURITY는 opt-out을
     * 무시해 연락 채널(EMAIL·SMS) 최소 1개를 강제한다(EMAIL 우선). 가용 채널이 전혀 없으면 빈
     * 집합을 반환한다(발송 불가 — 호출자가 스킵).
     */
    public Set<NotificationChannel> decide(
            NotificationCategory category,
            Set<NotificationChannel> allowedChannels,
            Set<NotificationChannel> availableChannels) {
        EnumSet<NotificationChannel> decided = EnumSet.noneOf(NotificationChannel.class);
        for (NotificationChannel channel : allowedChannels) {
            if (availableChannels.contains(channel)) {
                decided.add(channel);
            }
        }
        if (category == NotificationCategory.SECURITY && hasNoContactChannel(decided)) {
            if (availableChannels.contains(NotificationChannel.EMAIL)) {
                decided.add(NotificationChannel.EMAIL);
            } else if (availableChannels.contains(NotificationChannel.SMS)) {
                decided.add(NotificationChannel.SMS);
            }
        }
        return Set.copyOf(decided);
    }

    private boolean hasNoContactChannel(Set<NotificationChannel> channels) {
        return !channels.contains(NotificationChannel.EMAIL) && !channels.contains(NotificationChannel.SMS);
    }
}
