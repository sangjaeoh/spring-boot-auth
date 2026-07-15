package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.ConsentAction;
import com.example.auth.domain.user.entity.ConsentState;
import com.example.auth.domain.user.entity.TermsDocument;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.entity.TermsVersion;
import com.example.auth.domain.user.info.ConsentStateInfo;
import com.example.auth.domain.user.info.RequiredConsentGapInfo;
import com.example.auth.domain.user.repository.ConsentStateRepository;
import com.example.auth.domain.user.repository.TermsDocumentRepository;
import com.example.auth.domain.user.repository.TermsVersionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 동의 스냅샷과 필수 약관 재동의 필요 목록을 조회한다.
 *
 * <p>재동의 필요는 저장 상태가 아니라 현행 필수 버전 대조로 파생한다({@code ConsentPolicy} — 필수 약관
 * 새 버전 발행 시 기존 회원은 이 판정으로 재동의를 요구받는다).
 */
@Service
public class ConsentStateReader {

    private final ConsentStateRepository consentStateRepository;
    private final TermsDocumentRepository termsDocumentRepository;
    private final TermsVersionRepository termsVersionRepository;

    public ConsentStateReader(
            ConsentStateRepository consentStateRepository,
            TermsDocumentRepository termsDocumentRepository,
            TermsVersionRepository termsVersionRepository) {
        this.consentStateRepository = consentStateRepository;
        this.termsDocumentRepository = termsDocumentRepository;
        this.termsVersionRepository = termsVersionRepository;
    }

    /**
     * 회원의 현재 동의 스냅샷 전체를 반환한다.
     */
    @Transactional(readOnly = true)
    public List<ConsentStateInfo> getStates(UUID userId) {
        return consentStateRepository.findByUserId(userId).stream()
                .map(ConsentStateInfo::from)
                .toList();
    }

    /**
     * 현행 필수 버전에 대한 동의가 없는 필수 약관 목록을 반환한다(비면 이용 게이트 통과).
     */
    @Transactional(readOnly = true)
    public List<RequiredConsentGapInfo> findRequiredGaps(UUID userId) {
        Map<TermsType, Integer> currentVersions = currentVersionsByType(Instant.now());
        Map<TermsType, ConsentState> states = new HashMap<>();
        for (ConsentState state : consentStateRepository.findByUserId(userId)) {
            states.put(state.getTermsType(), state);
        }
        List<RequiredConsentGapInfo> gaps = new ArrayList<>();
        for (TermsDocument document : termsDocumentRepository.findByRequiredTrue()) {
            Integer requiredVersion = currentVersions.get(document.getType());
            if (requiredVersion == null) {
                continue;
            }
            ConsentState state = states.get(document.getType());
            Integer agreedVersion = agreedVersionOf(state);
            if (agreedVersion == null || agreedVersion < requiredVersion) {
                gaps.add(new RequiredConsentGapInfo(document.getType(), requiredVersion, agreedVersion));
            }
        }
        return gaps;
    }

    private static @Nullable Integer agreedVersionOf(@Nullable ConsentState state) {
        if (state == null || state.getCurrentAction() != ConsentAction.AGREE) {
            return null;
        }
        return state.getAgreedVersion();
    }

    private Map<TermsType, Integer> currentVersionsByType(Instant now) {
        Map<TermsType, Integer> current = new HashMap<>();
        for (TermsVersion version : termsVersionRepository.findByEffectiveFromLessThanEqual(now)) {
            current.merge(version.getType(), version.getVersion(), Integer::max);
        }
        return current;
    }
}
