package com.example.auth.domain.auth.repository;

import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * 인증 계정의 영속 포트다(Spring Data 인터페이스 = 도메인 포트).
 *
 * <p>소프트삭제 대상이 아니므로 base finder를 그대로 쓴다.
 */
public interface AuthAccountRepository extends JpaRepository<AuthAccount, UUID> {

    Optional<AuthAccount> findByLoginEmail(Email loginEmail);

    boolean existsByLoginEmail(Email loginEmail);

    /**
     * 계정 행을 배타 잠금으로 조회한다 — 로그인 수단 변경(해제 등)의 직렬화 지점이다. 동시 해제가 각자
     * "하나 남음"을 관측해 0이 되는 TOCTOU를 차단한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthAccount> findWithLockByUserId(UUID userId);
}
