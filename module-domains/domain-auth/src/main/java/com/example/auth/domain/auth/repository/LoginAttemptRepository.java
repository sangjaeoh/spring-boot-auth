package com.example.auth.domain.auth.repository;

import com.example.auth.domain.auth.entity.LoginAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 로그인 이력의 영속 포트다(append-only).
 */
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {}
