package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.IdentityVerification;
import com.example.auth.domain.user.entity.VerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {

    List<IdentityVerification> findByUserId(UUID userId);

    List<IdentityVerification> findByStatusAndExpiresAtBefore(VerificationStatus status, Instant cutoff);

    // 결과 PII 보존창 파기 대상: 요청이 창을 지났고 결과 암호문이 남아 있는 행(파생 파서가 embedded null 판정을 못 푼다).
    @Query("select v from IdentityVerification v where v.requestedAt <= :cutoff and v.result.name is not null")
    List<IdentityVerification> findWithResidualPiiRequestedBefore(Instant cutoff);
}
