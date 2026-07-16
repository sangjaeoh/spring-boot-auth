package com.example.auth.common.auth.jwt;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 서명키 자동 회전·스토어 수렴을 구동한다.
 *
 * <p>회전 판정은 스토어의 현재 키 나이 기준이라 어느 인스턴스가 먼저 판정해도 CAS로 한 번만 회전된다.
 * 재적재 주기는 다른 인스턴스의 회전을 서명·게시 경로에 반영하는 상한 지연이다(유예 창보다 충분히 짧게 —
 * 미지 kid 검증은 재적재 주기와 무관하게 디코더가 즉시 재적재한다).
 */
public class SigningKeyRotationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyRotationScheduler.class);

    private final SigningKeyRing signingKeyRing;
    private final Duration rotationPeriod;

    public SigningKeyRotationScheduler(SigningKeyRing signingKeyRing, Duration rotationPeriod) {
        this.signingKeyRing = signingKeyRing;
        this.rotationPeriod = rotationPeriod;
    }

    @Scheduled(
            initialDelayString = "${auth.jwt.jwks.rotation-check-interval:PT1H}",
            fixedDelayString = "${auth.jwt.jwks.rotation-check-interval:PT1H}")
    void checkRotationDue() {
        if (signingKeyRing.rotateIfDue(Instant.now(), rotationPeriod)) {
            log.info("서명키 자동 회전 완료(주기 {})", rotationPeriod);
        }
    }

    @Scheduled(
            initialDelayString = "${auth.jwt.jwks.refresh-interval:PT1M}",
            fixedDelayString = "${auth.jwt.jwks.refresh-interval:PT1M}")
    void refreshFromStore() {
        signingKeyRing.refresh();
    }
}
