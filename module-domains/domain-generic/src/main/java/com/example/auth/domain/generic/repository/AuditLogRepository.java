package com.example.auth.domain.generic.repository;

import com.example.auth.domain.generic.entity.AuditLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * 감사 로그의 영속 포트다 — append-only WORM이라 update/delete 오퍼레이션을 표면에 두지 않는다
 * ({@code JpaRepository} 상속 대신 필요한 메서드만 선언 노출, 표면 유지는 테스트가 강제).
 */
public interface AuditLogRepository extends Repository<AuditLog, UUID> {

    AuditLog save(AuditLog auditLog);

    Optional<AuditLog> findById(UUID id);

    Page<AuditLog> findAllByOrderByAtDesc(Pageable pageable);

    Page<AuditLog> findByTargetOrderByAtDesc(String target, Pageable pageable);
}
