package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.repository.PasswordCredentialRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 자격증명 파기를 담당한다(탈퇴 정리 — 이력 포함 물리 삭제).
 */
@Service
public class PasswordCredentialRemover {

    private final PasswordCredentialRepository repository;

    public PasswordCredentialRemover(PasswordCredentialRepository repository) {
        this.repository = repository;
    }

    /**
     * 자격증명과 재사용 금지 이력을 파기한다(미존재면 무시 — 멱등).
     */
    @Transactional
    public void purge(UUID userId) {
        repository.findById(userId).ifPresent(repository::delete);
    }
}
