package com.example.auth.app.api.facade;

import com.example.auth.app.api.presentation.v1.ConsentOverviewResponse;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.service.ConsentProcessor;
import com.example.auth.domain.user.service.ConsentStateReader;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 이용 중 동의 조회·동의/철회를 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지 않는다).
 */
@Component
public class ConsentFacade {

    private final ConsentStateReader consentStateReader;
    private final ConsentProcessor consentProcessor;

    public ConsentFacade(ConsentStateReader consentStateReader, ConsentProcessor consentProcessor) {
        this.consentStateReader = consentStateReader;
        this.consentProcessor = consentProcessor;
    }

    /**
     * 현재 동의 스냅샷과 필수 약관 재동의 필요 목록을 반환한다.
     */
    public ConsentOverviewResponse overview(UUID userId) {
        return ConsentOverviewResponse.of(
                consentStateReader.getStates(userId), consentStateReader.findRequiredGaps(userId));
    }

    /**
     * 현행 버전에 대한 동의(재동의 포함)를 기록한다.
     */
    public void agree(UUID userId, TermsType termsType, int termsVersion, @Nullable NotificationChannel channel) {
        consentProcessor.agree(userId, termsType, termsVersion, channel);
    }

    /**
     * 선택 약관 동의를 철회한다(필수 약관은 탈퇴 경로 안내 400).
     */
    public void withdraw(UUID userId, TermsType termsType) {
        consentProcessor.withdraw(userId, termsType);
    }
}
