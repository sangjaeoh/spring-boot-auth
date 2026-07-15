package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.TermsDocument;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.entity.TermsVersion;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.TermsDocumentRepository;
import com.example.auth.domain.user.repository.TermsVersionRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 동의 선택값을 현행 약관에 대조해 검증한다.
 *
 * <p>버퍼링 전 조기 실패용이다 — 버퍼~가입완료 사이 약관 개정 경합이 있으므로 최종 권위는
 * {@code CreateUser} 트랜잭션의 재검증이다(다음 슬라이스). 약관 테이블은 시드 소규모라 전량 로드 후
 * 인메모리로 판정한다.
 */
@Service
public class ConsentValidator {

    private final TermsDocumentRepository documentRepository;
    private final TermsVersionRepository versionRepository;

    public ConsentValidator(TermsDocumentRepository documentRepository, TermsVersionRepository versionRepository) {
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
    }

    /**
     * 동의 선택값이 (1) 전부 현행 발효 버전과 일치하고 (2) 필수 약관을 전부 포함함을 검증한다.
     *
     * @param agreed 동의한 (약관 유형 → 버전)
     * @throws UserException 현행 버전 불일치·미지 유형이면 {@code TERMS_VERSION_INVALID}(400), 필수 누락이면
     *     {@code REQUIRED_CONSENT_MISSING}(400)
     */
    @Transactional(readOnly = true)
    public void validateForSignup(Map<TermsType, Integer> agreed) {
        Map<TermsType, Integer> currentVersions = currentVersionsByType(Instant.now());
        for (Map.Entry<TermsType, Integer> entry : agreed.entrySet()) {
            Integer currentVersion = currentVersions.get(entry.getKey());
            if (!entry.getValue().equals(currentVersion)) {
                throw new UserException(UserErrorCode.TERMS_VERSION_INVALID);
            }
        }
        for (TermsDocument document : documentRepository.findByRequiredTrue()) {
            if (!agreed.containsKey(document.getType())) {
                throw new UserException(UserErrorCode.REQUIRED_CONSENT_MISSING);
            }
        }
    }

    private Map<TermsType, Integer> currentVersionsByType(Instant now) {
        Map<TermsType, Integer> current = new HashMap<>();
        for (TermsVersion version : versionRepository.findByEffectiveFromLessThanEqual(now)) {
            current.merge(version.getType(), version.getVersion(), Integer::max);
        }
        return current;
    }
}
