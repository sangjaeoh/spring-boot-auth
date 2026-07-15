package com.example.auth.domain.auth.service;

import com.example.auth.domain.auth.repository.SocialConnectionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 연동 파기를 담당한다(탈퇴 정리 — 전체 물리 삭제).
 *
 * <p>사용자 주도 개별 해제(≥1 로그인 수단 유지 검증)는 {@code SocialConnectionProcessor.disconnect}가
 * 소유한다 — 탈퇴는 계정 종결이라 수단 유지 불변식이 적용되지 않는다.
 */
@Service
public class SocialConnectionRemover {

    private final SocialConnectionRepository repository;

    public SocialConnectionRemover(SocialConnectionRepository repository) {
        this.repository = repository;
    }

    /**
     * 회원의 모든 소셜 연동을 파기한다(없으면 무시 — 멱등).
     */
    @Transactional
    public void purgeAll(UUID userId) {
        repository.deleteByUserId(userId);
    }
}
