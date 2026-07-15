package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.TermsVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsVersionRepository extends JpaRepository<TermsVersion, UUID> {

    List<TermsVersion> findByEffectiveFromLessThanEqual(Instant now);
}
