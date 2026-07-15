package com.example.auth.domain.user.service;

import com.example.auth.common.core.crypto.BlindIndexer;
import com.example.auth.domain.user.entity.VerificationResult;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.info.IdentityVerifiedInfo;
import com.example.auth.domain.user.port.IdentityProviderOutcome;
import com.example.auth.domain.user.port.IdentityProviderRequest;
import com.example.auth.domain.user.port.IdentityVerificationProvider;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 실명확인 플로우를 조율한다 — 온보딩(인증)이 유저 경계로 진입하는 단일 진입점이다(포트 직접 호출 금지).
 *
 * <p>자체 트랜잭션을 열지 않는다 — 기관 호출(외부 I/O)을 트랜잭션 밖에 두기 위해 요청 확정
 * ({@code Appender})·전이({@code Modifier})가 각자 짧은 트랜잭션을 연다. 기관 호출이 런타임 예외로
 * 실패하면 REQUESTED로 남는다(실기관에선 완료됐을 수 있는 미확정 상태라 FAILED로 오분류하지 않는다 —
 * {@code expiresAt} 기준 EXPIRED 스윕(P4)이 수렴 지점).
 *
 * <p>CI 원문은 이 메서드 수명 안에서만 존재한다 — salted HMAC({@code ciHash})으로 즉시 파생하고
 * 원문은 저장·반환하지 않는다. 완료 직후 CI 중복을 읽기 soft-check로 조기 판정한다(hard-enforce는
 * {@code CreateUser} 트랜잭션의 유니크 인덱스 — 다음 슬라이스).
 */
@Service
public class IdentityVerificationProcessor {

    private final IdentityVerificationProvider provider;
    private final IdentityVerificationAppender appender;
    private final IdentityVerificationModifier modifier;
    private final CiUniquenessValidator ciUniquenessValidator;
    private final BlindIndexer blindIndexer;

    public IdentityVerificationProcessor(
            IdentityVerificationProvider provider,
            IdentityVerificationAppender appender,
            IdentityVerificationModifier modifier,
            CiUniquenessValidator ciUniquenessValidator,
            BlindIndexer blindIndexer) {
        this.provider = provider;
        this.appender = appender;
        this.modifier = modifier;
        this.ciUniquenessValidator = ciUniquenessValidator;
        this.blindIndexer = blindIndexer;
    }

    /**
     * 주장된 정체성을 실명확인하고 성공 시 {@code (verificationId, ciHash)}를 반환한다.
     *
     * @throws UserException 기관 판정 실패 시(400), CI가 활성 회원에 이미 연결돼 있으면(409)
     */
    public IdentityVerifiedInfo verify(IdentityProviderRequest request) {
        UUID verificationId = appender.request(provider.provider(), Instant.now());
        IdentityProviderOutcome outcome = provider.verify(request);
        return switch (outcome) {
            case IdentityProviderOutcome.Verified verified -> {
                String ciHash = blindIndexer.blindIndex(verified.ci());
                VerificationResult result = VerificationResult.of(
                        verified.name(),
                        verified.birthDate(),
                        verified.gender(),
                        verified.carrier(),
                        verified.phone(),
                        ciHash,
                        verified.di());
                modifier.complete(verificationId, result, Instant.now());
                // soft-check가 거부해도 VERIFIED 행은 남는다 — 수행된 실명확인의 감사 사실.
                ciUniquenessValidator.checkAvailable(ciHash);
                yield new IdentityVerifiedInfo(verificationId, ciHash);
            }
            case IdentityProviderOutcome.Failed _ -> {
                modifier.fail(verificationId);
                throw new UserException(UserErrorCode.IDENTITY_VERIFICATION_FAILED);
            }
        };
    }
}
