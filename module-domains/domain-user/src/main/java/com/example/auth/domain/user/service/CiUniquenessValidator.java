package com.example.auth.domain.user.service;

import static java.util.Objects.requireNonNull;

import com.example.auth.domain.user.entity.CiRegistry;
import com.example.auth.domain.user.entity.CiStatus;
import com.example.auth.domain.user.exception.RejoinCooldownException;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.CiRegistryRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CI 중복가입·재가입 쿨다운을 읽기 조회로 조기 판정한다(soft-check).
 *
 * <p>이 판정은 TOCTOU에 노출된 조기 실패용이다 — 최종 유일성은 {@code CreateUser} 트랜잭션의
 * {@code ci_hash} 유니크 인덱스(신규)와 tombstone 행 잠금 + relink 가드(재가입)가 hard-enforce한다.
 * tombstone 내부는 노출하지 않고 판정 결과(거부·잔여일)만 내보낸다.
 */
@Service
public class CiUniquenessValidator {

    private final CiRegistryRepository repository;
    private final Duration rejoinCooldown;

    public CiUniquenessValidator(
            CiRegistryRepository repository, @Value("${user.retention.rejoin-cooldown-days:30}") long cooldownDays) {
        this.repository = repository;
        this.rejoinCooldown = Duration.ofDays(cooldownDays);
    }

    /**
     * 해당 CI로 신규 가입이 가능함을 검증한다.
     *
     * @throws UserException 활성 회원에 이미 연결된 CI면(409)
     * @throws RejoinCooldownException 탈퇴 후 쿨다운이 지나지 않은 CI면(409, 잔여일 포함)
     */
    @Transactional(readOnly = true)
    public void checkAvailable(String ciHash) {
        CiRegistry entry = repository.findByCiHash(ciHash).orElse(null);
        if (entry == null) {
            return;
        }
        if (entry.getStatus() == CiStatus.ACTIVE_LINKED) {
            throw new UserException(UserErrorCode.DUPLICATE_CI);
        }
        Instant eligibleAt = requireNonNull(entry.getWithdrawnAt(), "tombstone에는 withdrawnAt이 존재한다")
                .plus(rejoinCooldown);
        Instant now = Instant.now();
        if (now.isBefore(eligibleAt)) {
            // 잔여일은 올림 표기한다 — "1일 미만 남음"도 1일로 안내.
            long remainingDays = (Duration.between(now, eligibleAt).toSeconds() + 86_399) / 86_400;
            throw new RejoinCooldownException(remainingDays);
        }
    }
}
