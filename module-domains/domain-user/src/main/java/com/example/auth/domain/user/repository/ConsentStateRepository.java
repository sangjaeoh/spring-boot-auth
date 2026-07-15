package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.ConsentState;
import com.example.auth.domain.user.entity.TermsType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentStateRepository extends JpaRepository<ConsentState, UUID> {

    List<ConsentState> findByUserId(UUID userId);

    Optional<ConsentState> findByUserIdAndTermsType(UUID userId, TermsType termsType);

    long deleteByUserId(UUID userId);
}
