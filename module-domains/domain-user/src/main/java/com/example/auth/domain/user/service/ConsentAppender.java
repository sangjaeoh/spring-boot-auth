package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.ConsentAction;
import com.example.auth.domain.user.entity.ConsentRecord;
import com.example.auth.domain.user.entity.ConsentState;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.repository.ConsentRecordRepository;
import com.example.auth.domain.user.repository.ConsentStateRepository;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동의/철회 이력 append와 {@code ConsentState} fold 갱신을 한 트랜잭션으로 수행하는 쓰기 원자재다.
 *
 * <p>스냅샷은 로그의 fold 캐시라 같은 트랜잭션에서 갱신해야 로그와 항상 정합하다. 검증·이벤트 발행은
 * 호출자({@code ConsentProcessor}·{@code UserRegistrationProcessor})가 소유한다.
 */
@Service
public class ConsentAppender {

    private final ConsentRecordRepository consentRecordRepository;
    private final ConsentStateRepository consentStateRepository;

    public ConsentAppender(
            ConsentRecordRepository consentRecordRepository, ConsentStateRepository consentStateRepository) {
        this.consentRecordRepository = consentRecordRepository;
        this.consentStateRepository = consentStateRepository;
    }

    /**
     * 동의/철회 한 건을 append하고 스냅샷을 갱신한다.
     */
    @Transactional
    public void append(
            UUID userId,
            TermsType termsType,
            int termsVersion,
            ConsentAction action,
            @Nullable NotificationChannel channel,
            Instant at) {
        consentRecordRepository.save(ConsentRecord.create(userId, termsType, termsVersion, action, channel, at));
        ConsentState state = consentStateRepository
                .findByUserIdAndTermsType(userId, termsType)
                .orElse(null);
        if (state == null) {
            consentStateRepository.save(ConsentState.create(userId, termsType, action, termsVersion));
        } else {
            state.apply(action, termsVersion);
        }
    }
}
