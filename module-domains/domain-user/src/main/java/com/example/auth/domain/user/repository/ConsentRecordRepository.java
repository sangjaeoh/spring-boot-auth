package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.ConsentRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 동의 이력의 영속 포트다(append-only — save만 쓴다. update/delete 금지는 엔티티 불변 설계가 지킨다).
 */
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    long deleteByUserId(UUID userId);
}
