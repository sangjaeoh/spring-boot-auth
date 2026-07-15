package com.example.auth.domain.auth.service;

import com.example.auth.common.core.crypto.TokenHasher;
import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.domain.auth.port.NotificationChannel;
import com.example.auth.domain.auth.port.NotificationSender;
import com.example.auth.domain.auth.port.VerificationChallengeStore;
import com.example.auth.domain.auth.port.VerificationResult;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 원타임 인증코드의 발급·검증을 조율한다(Redis 저장·알림 발송은 포트에 위임 — RDB 트랜잭션 없음).
 *
 * <p>코드는 저엔트로피 숫자라 저장소엔 결정적 해시({@link TokenHasher})만 넘긴다 — 원문을 Redis 덤프·로그로
 * 노출하지 않기 위함이며, 코드 자체의 기밀은 시도상한·TTL·소진(single-use)이 지킨다(해시 되돌리기는 저비용).
 * 발급은 저장 후 발송 순서라 발송 실패가 코드 없는 고아 챌린지를 남기지 않는다.
 */
@Service
public class VerificationChallengeProcessor {

    private final VerificationChallengeStore store;
    private final TokenHasher tokenHasher;
    private final NotificationSender notificationSender;
    private final SecureRandom random = new SecureRandom();
    private final Duration codeTtl;
    private final int maxAttempts;
    private final int codeDigits;

    public VerificationChallengeProcessor(
            VerificationChallengeStore store,
            TokenHasher tokenHasher,
            NotificationSender notificationSender,
            @Value("${auth.verification.code-ttl-minutes:5}") long codeTtlMinutes,
            @Value("${auth.verification.max-attempts:5}") int maxAttempts,
            @Value("${auth.verification.code-digits:6}") int codeDigits) {
        this.store = store;
        this.tokenHasher = tokenHasher;
        this.notificationSender = notificationSender;
        this.codeTtl = Duration.ofMinutes(codeTtlMinutes);
        this.maxAttempts = maxAttempts;
        this.codeDigits = codeDigits;
    }

    /**
     * 새 인증코드를 발급해 저장하고 대상에게 발송한 뒤 챌린지ID를 반환한다.
     */
    public String issue(UUID subjectId, NotificationChannel channel, String target) {
        String challengeId = UuidV7Generator.generate().toString();
        String code = generateCode();
        store.issue(challengeId, subjectId, tokenHasher.hash(code), codeTtl, maxAttempts);
        notificationSender.send(channel, target, code);
        return challengeId;
    }

    /**
     * 제시된 코드를 원자 검증하고 결과(성공 시 바인딩된 subjectId 포함)를 반환한다.
     */
    public VerificationResult verify(String challengeId, String code) {
        return store.verify(challengeId, tokenHasher.hash(code));
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(codeDigits);
        for (int i = 0; i < codeDigits; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
