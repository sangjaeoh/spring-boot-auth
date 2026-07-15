package com.example.auth.app.batch.job;

import com.example.auth.domain.user.service.DormancyProcessor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 휴면 전환 잡 — 미접속 12개월 + 사전통지 30일 경과 회원을 DORMANT로 전환한다. 인증 스냅샷은 커밋 후
 * {@code UserStatusChanged} 소비가 반영한다(실패 시 DLQ 재시도 수렴).
 */
@Component
public class DormancyTransitionJob {

    private static final Logger log = LoggerFactory.getLogger(DormancyTransitionJob.class);

    private final DormancyProcessor dormancyProcessor;

    public DormancyTransitionJob(DormancyProcessor dormancyProcessor) {
        this.dormancyProcessor = dormancyProcessor;
    }

    /**
     * 전환 대상을 휴면 처리하고 전환된 회원 목록을 반환한다.
     */
    public List<UUID> run(Instant now) {
        List<UUID> transitioned = dormancyProcessor.transitionDue(now);
        log.info("휴면 전환 잡 완료 전환={}건", transitioned.size());
        return transitioned;
    }
}
