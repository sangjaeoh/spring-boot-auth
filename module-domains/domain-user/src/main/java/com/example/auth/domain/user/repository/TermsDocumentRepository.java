package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.TermsDocument;
import com.example.auth.domain.user.entity.TermsType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsDocumentRepository extends JpaRepository<TermsDocument, UUID> {

    List<TermsDocument> findByRequiredTrue();

    Optional<TermsDocument> findByType(TermsType type);
}
