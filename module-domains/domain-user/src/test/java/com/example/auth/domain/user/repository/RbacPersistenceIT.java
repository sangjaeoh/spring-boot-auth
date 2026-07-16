package com.example.auth.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.crypto.EnvelopeCipher;
import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.user.entity.Role;
import com.example.auth.domain.user.entity.RoleName;
import com.example.auth.domain.user.entity.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RBAC 영속 E2E: 마이그레이션 시드(역할 3종·권한 매핑) → {@code ddl-auto=validate} → 배정 저장/조회·
 * {@code (userId, roleId)} 유니크 강제를 실 PostgreSQL로 검증한다.
 */
@SpringBootTest
@Import({JpaConfig.class, RbacPersistenceIT.TestCryptoBeans.class})
@Testcontainers
class RbacPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void seedsRoleAndPermissionMaster() {
        assertThat(roleRepository.findAll())
                .extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.USER, RoleName.ADMIN, RoleName.SUPER_ADMIN);

        assertThat(permissionRepository.findAll())
                .extracting(permission -> permission.getResource() + ":" + permission.getAction())
                .containsExactlyInAnyOrder(
                        "member:read", "member:lock", "member:force-logout", "audit:read", "role:write");
    }

    @Test
    void enforcesUserRoleUniqueness() {
        Role role = roleRepository.findByName(RoleName.USER).orElseThrow();
        UUID userId = UUID.randomUUID();
        userRoleRepository.save(UserRole.create(userId, role.getId(), Instant.now()));

        assertThatThrownBy(() -> userRoleRepository.saveAndFlush(UserRole.create(userId, role.getId(), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsAssignmentsByUser() {
        Role role = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        UUID userId = UUID.randomUUID();
        userRoleRepository.save(UserRole.create(userId, role.getId(), Instant.now()));

        assertThat(userRoleRepository.findByUserId(userId))
                .extracting(UserRole::getRoleId)
                .containsExactly(role.getId());
        assertThat(userRoleRepository.findByUserIdAndRoleId(userId, role.getId()))
                .isPresent();
    }

    /**
     * PII 컨버터(User 엔티티)가 요구하는 crypto 포트의 no-op fake다 — 이 IT는 RBAC 테이블만 다루므로
     * EMF 부팅용 배선만 충족한다(실 배선 검증은 UserPersistenceIT 소유).
     */
    @TestConfiguration
    static class TestCryptoBeans {

        @Bean
        EnvelopeCipher envelopeCipher() {
            return new EnvelopeCipher() {
                @Override
                public String encrypt(String plaintext) {
                    return plaintext;
                }

                @Override
                public String decrypt(String ciphertext) {
                    return ciphertext;
                }
            };
        }
    }
}
