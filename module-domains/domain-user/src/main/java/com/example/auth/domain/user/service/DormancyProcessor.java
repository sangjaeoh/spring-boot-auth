package com.example.auth.domain.user.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.event.UserStatusChanged;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴면 생명주기를 집행한다 — 사전통지 마킹, 미접속 기간 경과 전환, OTP 재인증 후 해제.
 *
 * <p>미접속 기산점은 {@code coalesce(lastLoginAt, createdAt)}이고 기간(12개월)·사전통지(30일 전)는 설정
 * 외부화 정책값이다. 전환·해제는 {@code UserStatusChanged}(단조 version)를 발행해 인증 스냅샷이 멱등
 * 동기화된다. 실제 통지 발송은 알림 슬라이스 소관이라 여기서는 발송 사실 마킹과 구조적 로그만 남긴다.
 */
@Service
public class DormancyProcessor {

    private static final Logger log = LoggerFactory.getLogger(DormancyProcessor.class);

    private final UserRepository userRepository;
    private final MessagePublisher messagePublisher;
    private final int dormancyMonths;
    private final int noticeDays;

    public DormancyProcessor(
            UserRepository userRepository,
            MessagePublisher messagePublisher,
            @Value("${user.retention.dormancy-months:12}") int dormancyMonths,
            @Value("${user.retention.dormancy-notice-days:30}") int noticeDays) {
        this.userRepository = userRepository;
        this.messagePublisher = messagePublisher;
        this.dormancyMonths = dormancyMonths;
        this.noticeDays = noticeDays;
    }

    /**
     * 휴면 전환이 임박한(미접속 ≥ 12개월−30일) 미통지 회원에게 사전통지를 마킹하고 대상 수를 반환한다.
     */
    @Transactional
    public int notifyUpcoming(Instant now) {
        Instant noticeCutoff =
                utc(now).minusMonths(dormancyMonths).plusDays(noticeDays).toInstant();
        List<User> candidates = userRepository.findDormancyNoticeCandidates(LifecycleStatus.ACTIVE, noticeCutoff);
        for (User user : candidates) {
            user.markDormancyNotified(now);
            log.info("휴면 사전통지 대상 userId={} lastLoginAt={}", user.getId(), user.getLastLoginAt());
        }
        return candidates.size();
    }

    /**
     * 미접속 12개월 경과 + 사전통지 후 30일이 지난 회원을 휴면 전환하고 전환된 회원 목록을 반환한다.
     */
    @Transactional
    public List<UUID> transitionDue(Instant now) {
        Instant inactivityCutoff = utc(now).minusMonths(dormancyMonths).toInstant();
        Instant noticeAgeCutoff = utc(now).minusDays(noticeDays).toInstant();
        List<UUID> transitioned = new ArrayList<>();
        for (User user : userRepository.findDormancyTransitionCandidates(
                LifecycleStatus.ACTIVE, noticeAgeCutoff, inactivityCutoff)) {
            user.makeDormant(now);
            messagePublisher.publish(
                    new UserStatusChanged(user.getId(), LifecycleStatus.DORMANT, user.getStatusVersion(), now));
            transitioned.add(user.getId());
        }
        return transitioned;
    }

    /**
     * 휴면을 해제하고(OTP 재인증 선행 — 호출측 파사드가 검증) 스냅샷 동기화용 단조 버전을 반환한다.
     * 유저 권위 상태만 신뢰한다 — 인증 스냅샷이 어떻게 보이든 실제 DORMANT가 아니면 거부한다.
     *
     * @throws UserException 미존재 회원이면(404), DORMANT가 아니면(409)
     */
    @Transactional
    public long reactivate(UUID userId, Instant now) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        user.reactivate(now);
        messagePublisher.publish(new UserStatusChanged(userId, LifecycleStatus.ACTIVE, user.getStatusVersion(), now));
        return user.getStatusVersion();
    }

    private static ZonedDateTime utc(Instant now) {
        return ZonedDateTime.ofInstant(now, ZoneOffset.UTC);
    }
}
