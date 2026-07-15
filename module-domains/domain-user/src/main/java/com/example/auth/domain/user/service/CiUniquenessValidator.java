package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.CiRegistry;
import com.example.auth.domain.user.entity.CiStatus;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.CiRegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CI 중복가입을 읽기 조회로 조기 판정한다(soft-check).
 *
 * <p>이 판정은 TOCTOU에 노출된 조기 실패용이다 — 최종 유일성은 {@code CreateUser} 트랜잭션의
 * {@code ci_hash} 유니크 인덱스가 hard-enforce한다(다음 슬라이스). WITHDRAWN_RETAINED tombstone의
 * 재가입 쿨다운 판정은 탈퇴가 등장하는 P4에서 배선한다(그때까지 통과).
 */
@Service
public class CiUniquenessValidator {

    private final CiRegistryRepository repository;

    public CiUniquenessValidator(CiRegistryRepository repository) {
        this.repository = repository;
    }

    /**
     * 해당 CI로 신규 가입이 가능함을 검증한다.
     *
     * @throws UserException 활성 회원에 이미 연결된 CI면(409)
     */
    @Transactional(readOnly = true)
    public void checkAvailable(String ciHash) {
        CiRegistry entry = repository.findByCiHash(ciHash).orElse(null);
        if (entry != null && entry.getStatus() == CiStatus.ACTIVE_LINKED) {
            throw new UserException(UserErrorCode.DUPLICATE_CI);
        }
    }
}
