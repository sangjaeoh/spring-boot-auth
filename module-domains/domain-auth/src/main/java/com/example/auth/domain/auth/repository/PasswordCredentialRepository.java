package com.example.auth.domain.auth.repository;

import com.example.auth.domain.auth.entity.PasswordCredential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 비밀번호 자격증명의 영속 포트다.
 */
public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, UUID> {}
