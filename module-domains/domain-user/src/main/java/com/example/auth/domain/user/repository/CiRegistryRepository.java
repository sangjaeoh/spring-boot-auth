package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.CiRegistry;
import com.example.auth.domain.user.entity.CiStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CiRegistryRepository extends JpaRepository<CiRegistry, UUID> {

    Optional<CiRegistry> findByCiHash(String ciHash);

    /**
     * 재가입 relink 직렬화용 잠금 조회다 — 신규 insert 경합은 유니크 인덱스가, tombstone 재연결 경합은
     * 이 행 잠금 + {@code relink} 상태 가드가 backstop한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CiRegistry> findWithLockByCiHash(String ciHash);

    Optional<CiRegistry> findByLinkedUserId(UUID linkedUserId);

    List<CiRegistry> findByStatusAndWithdrawnAtBefore(CiStatus status, Instant cutoff);
}
