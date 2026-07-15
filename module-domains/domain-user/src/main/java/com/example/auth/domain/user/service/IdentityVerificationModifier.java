package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.IdentityVerification;
import com.example.auth.domain.user.entity.VerificationResult;
import com.example.auth.domain.user.repository.IdentityVerificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실명확인 상태 전이를 담당한다(전이 가드는 엔티티가 소유, 수정은 dirty checking).
 */
@Service
public class IdentityVerificationModifier {

    private final IdentityVerificationRepository repository;

    public IdentityVerificationModifier(IdentityVerificationRepository repository) {
        this.repository = repository;
    }

    /**
     * 실명확인 성공 결과를 확정한다(REQUESTED→VERIFIED).
     */
    @Transactional
    public void complete(UUID verificationId, VerificationResult result, Instant verifiedAt) {
        IdentityVerification verification = getVerification(verificationId);
        verification.complete(result, verifiedAt);
    }

    /**
     * 실명확인을 실패로 종결한다(REQUESTED→FAILED).
     */
    @Transactional
    public void fail(UUID verificationId) {
        IdentityVerification verification = getVerification(verificationId);
        verification.fail();
    }

    private IdentityVerification getVerification(UUID verificationId) {
        // 같은 흐름에서 방금 생성한 id만 도달하므로 부재는 외부 입력이 아니라 호출자 버그다.
        return repository
                .findById(verificationId)
                .orElseThrow(() -> new IllegalArgumentException("본인인증이 존재하지 않습니다: " + verificationId));
    }
}
