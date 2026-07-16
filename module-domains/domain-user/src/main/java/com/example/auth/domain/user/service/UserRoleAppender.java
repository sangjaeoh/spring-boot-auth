package com.example.auth.domain.user.service;

import com.example.auth.domain.user.entity.Role;
import com.example.auth.domain.user.entity.RoleName;
import com.example.auth.domain.user.entity.UserRole;
import com.example.auth.domain.user.repository.RoleRepository;
import com.example.auth.domain.user.repository.UserRoleRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 기본 역할 배정을 담당한다.
 */
@Service
public class UserRoleAppender {

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public UserRoleAppender(RoleRepository roleRepository, UserRoleRepository userRoleRepository) {
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * 신규 회원에게 기본 역할(USER)을 배정한다 — {@code CreateUser} 트랜잭션에 참여한다.
     *
     * <p>인증의 roles 투영 초기값({@code USER})과 일치하므로 {@code RoleChanged}를 발행하지 않는다.
     */
    @Transactional
    public void assignDefault(UUID userId, Instant now) {
        // 역할 마스터는 마이그레이션 시드 전제 — 부재는 외부 입력이 아니라 배포 결함이다.
        Role role = roleRepository
                .findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("USER 역할 시드가 없습니다"));
        userRoleRepository.save(UserRole.create(userId, role.getId(), now));
    }
}
