package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.TermsDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsDocumentRepository extends JpaRepository<TermsDocument, UUID> {

    List<TermsDocument> findByRequiredTrue();
}
