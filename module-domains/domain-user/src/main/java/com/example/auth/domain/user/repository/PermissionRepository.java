package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.Permission;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 권한 마스터의 영속 포트다(시드 데이터 read-only).
 */
public interface PermissionRepository extends JpaRepository<Permission, UUID> {}
