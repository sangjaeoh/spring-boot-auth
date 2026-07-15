package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.IdentityVerification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {}
