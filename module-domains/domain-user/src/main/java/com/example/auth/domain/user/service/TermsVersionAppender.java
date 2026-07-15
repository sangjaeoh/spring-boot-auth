package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.entity.TermsVersion;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.TermsDocumentRepository;
import com.example.auth.domain.user.repository.TermsVersionRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 약관 새 버전을 발행한다({@code publishVersion}).
 *
 * <p>버전은 직전+1로 단조 증가하고 {@code (type, version)} 유니크 인덱스가 동시 발행을 backstop한다.
 * 발행 후 버전은 불변이다. 필수 약관의 새 버전 발행은 기존 회원을 재동의 필요 상태로 만든다 — 별도
 * 상태 저장 없이 이용 게이트({@code ConsentStateReader.findRequiredGaps})가 현행 버전 대조로 파생한다.
 */
@Service
public class TermsVersionAppender {

    private final TermsDocumentRepository documentRepository;
    private final TermsVersionRepository versionRepository;

    public TermsVersionAppender(TermsDocumentRepository documentRepository, TermsVersionRepository versionRepository) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
    }

    /**
     * 해당 유형의 새 버전을 발행하고 발행된 버전 번호를 반환한다.
     *
     * @throws UserException 등록되지 않은 약관 유형이면(404)
     */
    @Transactional
    public int publish(TermsType type, String content, Instant effectiveFrom) {
        if (documentRepository.findByType(type).isEmpty()) {
            throw new UserException(UserErrorCode.TERMS_TYPE_NOT_FOUND);
        }
        int nextVersion = versionRepository
                        .findTopByTypeOrderByVersionDesc(type)
                        .map(TermsVersion::getVersion)
                        .orElse(0)
                + 1;
        versionRepository.save(TermsVersion.create(type, nextVersion, content, effectiveFrom));
        return nextVersion;
    }
}
