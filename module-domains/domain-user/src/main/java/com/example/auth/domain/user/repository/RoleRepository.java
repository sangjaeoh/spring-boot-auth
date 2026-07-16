package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.Role;
import com.example.auth.domain.user.entity.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 역할 마스터의 영속 포트다(시드 데이터 read-only).
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);
}
