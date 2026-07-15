package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.entity.LoginAttempt;
import com.example.auth.domain.auth.repository.LoginAttemptRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 이력의 보존 규칙을 집행한다 — IP는 90일 후 가명화(레코드·순서 불변), 이력 자체는 2년 경과 시
 * 파기한다(안전성 확보조치 기준 §8① — 값은 설정 외부화).
 */
@Service
public class LoginAttemptRemover {

    private final LoginAttemptRepository repository;
    private final int ipAnonymizeDays;
    private final int retentionYears;

    public LoginAttemptRemover(
            LoginAttemptRepository repository,
            @Value("${auth.retention.login-ip-anonymize-days:90}") int ipAnonymizeDays,
            @Value("${auth.retention.login-attempt-years:2}") int retentionYears) {
        this.repository = repository;
        this.ipAnonymizeDays = ipAnonymizeDays;
        this.retentionYears = retentionYears;
    }

    /**
     * 가명화 창(90일)을 지난 기록의 IP를 뒤 절단 가명화하고 처리 건수를 반환한다.
     */
    @Transactional
    public int anonymizeStaleIps(Instant now) {
        Instant cutoff = now.minusSeconds(ipAnonymizeDays * 86_400L);
        int anonymized = 0;
        for (LoginAttempt attempt : repository.findByAtBefore(cutoff)) {
            if (!attempt.isIpAnonymized()) {
                attempt.anonymizeIp();
                anonymized++;
            }
        }
        return anonymized;
    }

    /**
     * 보존창(2년)을 지난 이력을 파기하고 삭제 건수를 반환한다.
     */
    @Transactional
    public long purgeExpired(Instant now) {
        Instant cutoff = ZonedDateTime.ofInstant(now, ZoneOffset.UTC)
                .minusYears(retentionYears)
                .toInstant();
        return repository.deleteByAtBefore(cutoff);
    }
}
