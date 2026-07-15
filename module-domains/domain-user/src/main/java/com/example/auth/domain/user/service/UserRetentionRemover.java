package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.CiRegistry;
import com.example.auth.domain.user.entity.CiStatus;
import com.example.auth.domain.user.entity.IdentityVerification;
import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.entity.VerificationStatus;
import com.example.auth.domain.user.repository.CiRegistryRepository;
import com.example.auth.domain.user.repository.ConsentRecordRepository;
import com.example.auth.domain.user.repository.ConsentStateRepository;
import com.example.auth.domain.user.repository.IdentityVerificationRepository;
import com.example.auth.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 도메인 데이터의 보존·파기 스케줄을 집행한다(데이터 소유 = 파기 소유).
 *
 * <p>본인인증 진행 만료 스윕(REQUESTED 잔존 수렴 — FAILED 오분류 금지), 본인인증 결과 PII 파기(불필요분
 * 5일), CI tombstone 보존창(6개월) 경과 파기, 탈퇴 후 동의이력 보존창(5년) 경과 파기를 담당한다. 값은
 * 전부 설정 외부화 정책이다.
 */
@Service
public class UserRetentionRemover {

    private final IdentityVerificationRepository identityVerificationRepository;
    private final CiRegistryRepository ciRegistryRepository;
    private final UserRepository userRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final ConsentStateRepository consentStateRepository;
    private final int verificationPiiPurgeDays;
    private final int ciTombstoneMonths;
    private final int consentRetentionYears;

    public UserRetentionRemover(
            IdentityVerificationRepository identityVerificationRepository,
            CiRegistryRepository ciRegistryRepository,
            UserRepository userRepository,
            ConsentRecordRepository consentRecordRepository,
            ConsentStateRepository consentStateRepository,
            @Value("${user.retention.verification-pii-purge-days:5}") int verificationPiiPurgeDays,
            @Value("${user.retention.ci-tombstone-months:6}") int ciTombstoneMonths,
            @Value("${user.retention.consent-retention-years:5}") int consentRetentionYears) {
        this.identityVerificationRepository = identityVerificationRepository;
        this.ciRegistryRepository = ciRegistryRepository;
        this.userRepository = userRepository;
        this.consentRecordRepository = consentRecordRepository;
        this.consentStateRepository = consentStateRepository;
        this.verificationPiiPurgeDays = verificationPiiPurgeDays;
        this.ciTombstoneMonths = ciTombstoneMonths;
        this.consentRetentionYears = consentRetentionYears;
    }

    /**
     * 진행 만료가 지난 REQUESTED 본인인증을 EXPIRED로 수렴시키고 처리 건수를 반환한다 — 포트 런타임
     * 예외로 남은 잔존의 수렴 지점이다(기관에선 완료됐을 수 있어 FAILED로 오분류하지 않는다).
     */
    @Transactional
    public int expireStaleVerifications(Instant now) {
        List<IdentityVerification> stale =
                identityVerificationRepository.findByStatusAndExpiresAtBefore(VerificationStatus.REQUESTED, now);
        stale.forEach(IdentityVerification::expire);
        return stale.size();
    }

    /**
     * 파기창(5일)을 지난 본인인증 결과 PII를 파기하고 처리 건수를 반환한다(요청·상태 사실은 유지).
     */
    @Transactional
    public int purgeStaleVerificationResults(Instant now) {
        Instant cutoff = now.minusSeconds(verificationPiiPurgeDays * 86_400L);
        List<IdentityVerification> stale = identityVerificationRepository.findWithResidualPiiRequestedBefore(cutoff);
        stale.forEach(IdentityVerification::purgeResult);
        return stale.size();
    }

    /**
     * 보존창(6개월)이 지난 CI tombstone을 원장에서 완전 파기하고 삭제 건수를 반환한다.
     */
    @Transactional
    public int purgeExpiredCiTombstones(Instant now) {
        Instant cutoff = utc(now).minusMonths(ciTombstoneMonths).toInstant();
        List<CiRegistry> expired =
                ciRegistryRepository.findByStatusAndWithdrawnAtBefore(CiStatus.WITHDRAWN_RETAINED, cutoff);
        ciRegistryRepository.deleteAll(expired);
        return expired.size();
    }

    /**
     * 탈퇴 후 보존창(5년)이 지난 회원의 동의이력·스냅샷을 파기하고 대상 회원 수를 반환한다.
     */
    @Transactional
    public int purgeExpiredWithdrawnConsents(Instant now) {
        Instant cutoff = utc(now).minusYears(consentRetentionYears).toInstant();
        List<User> expired = userRepository.findByStatusAndWithdrawnAtBefore(LifecycleStatus.WITHDRAWN, cutoff);
        for (User user : expired) {
            consentRecordRepository.deleteByUserId(user.getId());
            consentStateRepository.deleteByUserId(user.getId());
        }
        return expired.size();
    }

    private static ZonedDateTime utc(Instant now) {
        return ZonedDateTime.ofInstant(now, ZoneOffset.UTC);
    }
}
