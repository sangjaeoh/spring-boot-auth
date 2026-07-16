package com.example.auth.domain.auth.repository;

import com.example.auth.domain.auth.entity.PasswordCredential;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 비밀번호 자격증명의 영속 포트다.
 */
public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, UUID> {

    // 재사용 금지 대조는 이력 해시 값만 필요하다. 값 프로젝션이라 트랜잭션 밖 KDF 대조에 지연로딩 없이 쓴다.
    @Query("select h.passwordHash from PasswordHistoryEntry h where h.credential.userId = :userId")
    List<String> findHistoryHashes(@Param("userId") UUID userId);
}
