package com.example.auth.domain.auth.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.LoginAttempt;
import com.example.auth.domain.auth.entity.LoginResult;
import com.example.auth.domain.auth.event.NewLocationDetected;
import com.example.auth.domain.auth.info.RiskAssessmentInfo;
import com.example.auth.domain.auth.port.GeoIpLookup;
import com.example.auth.domain.auth.port.GeoLocation;
import com.example.auth.domain.auth.repository.LoginAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 위험도를 평가한다(DOMAIN_MODEL §2.6): 신규 기기·신규 지역·최근 실패 이력을 합산한
 * {@code riskScore}를 산출하고, 신규 지역이면 {@link NewLocationDetected}를 발행한다.
 *
 * <p>신규 지역은 최근 성공 이력의 국가 집합에 없는 국가다 — 성공 이력이 전혀 없으면(최초 로그인)
 * 신규 지역으로 보지 않는다(신규 기기 신호가 이미 커버). 위험 임계 초과 시 스텝업은 MFA 확장
 * 슬롯으로 자리만 둔다(구현 범위 밖). 이름은 DOMAIN_MODEL 도메인 서비스 표의 명명을 따른다
 * ({@code Evaluator} 접미사 예외).
 */
@Service
public class RiskEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RiskEvaluator.class);

    private static final int NEW_DEVICE_SCORE = 40;
    private static final int NEW_LOCATION_SCORE = 40;
    private static final int FAILURE_SCORE_PER_ATTEMPT = 4;
    private static final int FAILURE_SCORE_CAP = 20;
    private static final Duration RECENT_FAILURE_WINDOW = Duration.ofHours(24);

    private final LoginAttemptRepository loginAttemptRepository;
    private final GeoIpLookup geoIpLookup;
    private final MessagePublisher messagePublisher;
    private final int stepUpThreshold;

    public RiskEvaluator(
            LoginAttemptRepository loginAttemptRepository,
            GeoIpLookup geoIpLookup,
            MessagePublisher messagePublisher,
            @Value("${auth.risk.step-up-threshold:80}") int stepUpThreshold) {
        this.loginAttemptRepository = loginAttemptRepository;
        this.geoIpLookup = geoIpLookup;
        this.messagePublisher = messagePublisher;
        this.stepUpThreshold = stepUpThreshold;
    }

    /**
     * 자격 검증에 성공한 로그인의 위험도를 평가한다 — 이력 기록용 점수와 현재 국가를 반환하고,
     * 신규 지역이면 감지 이벤트를 발행한다.
     */
    @Transactional(readOnly = true)
    public RiskAssessmentInfo evaluate(UUID userId, boolean newDevice, String ip, Instant now) {
        String countryCode =
                geoIpLookup.lookup(ip).map(GeoLocation::countryCode).orElse(null);
        int score = 0;
        if (newDevice) {
            score += NEW_DEVICE_SCORE;
        }
        if (countryCode != null && isNewLocation(userId, countryCode)) {
            score += NEW_LOCATION_SCORE;
            messagePublisher.publish(new NewLocationDetected(userId, countryCode, now));
        }
        long recentFailures = loginAttemptRepository.countByUserIdAndResultAndAtAfter(
                userId, LoginResult.FAILURE, now.minus(RECENT_FAILURE_WINDOW));
        score += (int) Math.min(recentFailures * FAILURE_SCORE_PER_ATTEMPT, FAILURE_SCORE_CAP);
        if (score >= stepUpThreshold) {
            // MFA 확장 슬롯(DOMAIN_MODEL): 위험 임계 초과 시 스텝업 챌린지를 트리거할 자리 — 구현 범위 밖.
            log.info("위험 임계 초과(스텝업 슬롯): userId={} riskScore={}", userId, score);
        }
        return new RiskAssessmentInfo(score, countryCode);
    }

    private boolean isNewLocation(UUID userId, String countryCode) {
        List<LoginAttempt> recentSuccesses =
                loginAttemptRepository.findTop20ByUserIdAndResultOrderByAtDesc(userId, LoginResult.SUCCESS);
        List<String> knownCountries = recentSuccesses.stream()
                .map(LoginAttempt::getCountryCode)
                .filter(known -> known != null)
                .distinct()
                .toList();
        return !knownCountries.isEmpty() && !knownCountries.contains(countryCode);
    }
}
