package com.example.auth.app.batch;

import com.example.auth.app.batch.job.DormancyNoticeJob;
import com.example.auth.app.batch.job.DormancyTransitionJob;
import com.example.auth.app.batch.job.RetentionPurgeJob;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * {@code app.batch.jobs}(CSV)에 지정된 잡을 순서대로 1회 실행한다(스케줄은 배포 크론이 소유 —
 * 예: {@code --app.batch.jobs=dormancy-notice,dormancy-transition,retention-purge}).
 */
@Component
public class BatchJobRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchJobRunner.class);

    private final DormancyNoticeJob dormancyNoticeJob;
    private final DormancyTransitionJob dormancyTransitionJob;
    private final RetentionPurgeJob retentionPurgeJob;
    private final List<String> jobs;

    public BatchJobRunner(
            DormancyNoticeJob dormancyNoticeJob,
            DormancyTransitionJob dormancyTransitionJob,
            RetentionPurgeJob retentionPurgeJob,
            @Value("${app.batch.jobs:}") String jobsCsv) {
        this.dormancyNoticeJob = dormancyNoticeJob;
        this.dormancyTransitionJob = dormancyTransitionJob;
        this.retentionPurgeJob = retentionPurgeJob;
        this.jobs = Arrays.stream(jobsCsv.split(",", -1))
                .map(String::trim)
                .filter(job -> !job.isEmpty())
                .toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (jobs.isEmpty()) {
            log.info("실행할 잡이 지정되지 않았다 — app.batch.jobs=dormancy-notice|dormancy-transition|retention-purge");
            return;
        }
        Instant now = Instant.now();
        for (String job : jobs) {
            switch (job) {
                case "dormancy-notice" -> dormancyNoticeJob.run(now);
                case "dormancy-transition" -> dormancyTransitionJob.run(now);
                case "retention-purge" -> retentionPurgeJob.run(now);
                default -> throw new IllegalArgumentException("미지의 잡: " + job);
            }
        }
    }
}
