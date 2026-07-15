package com.example.auth.app.batch.job;

import com.example.auth.domain.auth.service.LoginAttemptRemover;
import com.example.auth.domain.user.service.UserRetentionRemover;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 보존 파기 잡 — RetentionPolicy 설정값대로 각 도메인의 파기 서비스를 구동한다(데이터 소유=파기 소유).
 * append-only 이력은 레코드·순서를 유지한 채 PII만 가명화/파기하고, 보존창 경과분만 물리 파기한다.
 */
@Component
public class RetentionPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurgeJob.class);

    private final UserRetentionRemover userRetentionRemover;
    private final LoginAttemptRemover loginAttemptRemover;

    public RetentionPurgeJob(UserRetentionRemover userRetentionRemover, LoginAttemptRemover loginAttemptRemover) {
        this.userRetentionRemover = userRetentionRemover;
        this.loginAttemptRemover = loginAttemptRemover;
    }

    /**
     * 전 파기 스윕을 실행한다 — 본인인증 EXPIRED 수렴·결과 PII 파기, CI tombstone 파기, 탈퇴 후 동의이력
     * 파기, 로그인이력 IP 가명화·보존창 파기.
     */
    public void run(Instant now) {
        int expired = userRetentionRemover.expireStaleVerifications(now);
        int purgedResults = userRetentionRemover.purgeStaleVerificationResults(now);
        int purgedTombstones = userRetentionRemover.purgeExpiredCiTombstones(now);
        int purgedConsents = userRetentionRemover.purgeExpiredWithdrawnConsents(now);
        int anonymizedIps = loginAttemptRemover.anonymizeStaleIps(now);
        long purgedAttempts = loginAttemptRemover.purgeExpired(now);
        log.info(
                "보존 파기 잡 완료 verificationExpired={} verificationPiiPurged={} ciTombstonePurged={}"
                        + " withdrawnConsentPurged={} ipAnonymized={} loginAttemptPurged={}",
                expired,
                purgedResults,
                purgedTombstones,
                purgedConsents,
                anonymizedIps,
                purgedAttempts);
    }
}
