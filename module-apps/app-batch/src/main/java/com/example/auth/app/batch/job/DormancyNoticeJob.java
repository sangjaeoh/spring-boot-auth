package com.example.auth.app.batch.job;

import com.example.auth.domain.user.service.DormancyProcessor;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 휴면 사전통지 잡 — 전환 임박(미접속 12개월−30일) 회원에게 통지 사실을 마킹한다. 실제 발송은 알림
 * 슬라이스가 이 마킹을 소비할 때 배선한다.
 */
@Component
public class DormancyNoticeJob {

    private static final Logger log = LoggerFactory.getLogger(DormancyNoticeJob.class);

    private final DormancyProcessor dormancyProcessor;

    public DormancyNoticeJob(DormancyProcessor dormancyProcessor) {
        this.dormancyProcessor = dormancyProcessor;
    }

    /**
     * 사전통지 대상을 마킹하고 처리 건수를 반환한다.
     */
    public int run(Instant now) {
        int notified = dormancyProcessor.notifyUpcoming(now);
        log.info("휴면 사전통지 잡 완료 대상={}건", notified);
        return notified;
    }
}
