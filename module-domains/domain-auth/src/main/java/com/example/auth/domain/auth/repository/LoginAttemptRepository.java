package com.example.auth.domain.auth.repository;

import com.example.auth.domain.auth.entity.LoginAttempt;
import com.example.auth.domain.auth.entity.LoginResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 로그인 이력의 영속 포트다(append-only — 보존창 파기·IP 가명화만 수정 경로).
 */
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    List<LoginAttempt> findByAtBefore(Instant cutoff);

    long deleteByAtBefore(Instant cutoff);

    List<LoginAttempt> findTop20ByUserIdAndResultOrderByAtDesc(UUID userId, LoginResult result);

    Page<LoginAttempt> findByUserId(UUID userId, Pageable pageable);

    long countByUserIdAndResultAndAtAfter(UUID userId, LoginResult result, Instant after);
}
