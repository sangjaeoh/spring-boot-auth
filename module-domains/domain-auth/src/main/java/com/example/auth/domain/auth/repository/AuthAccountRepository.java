package com.example.auth.domain.auth.repository;

import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 인증 계정의 영속 포트다(Spring Data 인터페이스 = 도메인 포트).
 *
 * <p>소프트삭제 대상이 아니므로 base finder를 그대로 쓴다.
 */
public interface AuthAccountRepository extends JpaRepository<AuthAccount, UUID> {

    Optional<AuthAccount> findByLoginEmail(Email loginEmail);

    boolean existsByLoginEmail(Email loginEmail);
}
