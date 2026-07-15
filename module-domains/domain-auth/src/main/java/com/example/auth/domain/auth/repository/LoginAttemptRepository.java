package com.example.auth.domain.auth.repository;

import com.example.auth.domain.auth.entity.LoginAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 로그인 이력의 영속 포트다(append-only — 보존창 파기·IP 가명화만 수정 경로).
 */
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    List<LoginAttempt> findByAtBefore(Instant cutoff);

    long deleteByAtBefore(Instant cutoff);
}
