package com.example.auth.domain.user.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.user.entity.Role;
import com.example.auth.domain.user.entity.RoleName;
import com.example.auth.domain.user.entity.UserRole;
import com.example.auth.domain.user.event.RoleChanged;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.info.RoleAssignmentInfo;
import com.example.auth.domain.user.repository.RoleRepository;
import com.example.auth.domain.user.repository.UserRepository;
import com.example.auth.domain.user.repository.UserRoleRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할 배정 변경(관리자 오퍼레이션)을 소유한다 — 배정·해제 후 {@link RoleChanged}(변경 후 전체 스냅샷)를
 * 발행해 인증의 토큰 클레임 투영을 갱신시킨다.
 *
 * <p>해제는 배정 행 삭제다(순수 배정 행 — 변경 이력은 감사 로그가 소유). 반영 수준은 재발급 시 반영이다:
 * 기존 Access 토큰은 만료(TTL)까지 이전 역할을 유지하고, 즉시 차단이 필요하면 관리자가 강제 로그아웃을
 * 병행한다.
 */
@Service
public class RoleAssignmentProcessor {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final MessagePublisher messagePublisher;

    public RoleAssignmentProcessor(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            MessagePublisher messagePublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 역할을 배정하고 전/후 역할명 스냅샷을 반환한다.
     *
     * @throws UserException 회원 미존재(404), 역할 미존재(404), 이미 배정된 역할이면(409)
     */
    @Transactional
    public RoleAssignmentInfo assignRole(UUID userId, RoleName roleName, Instant now) {
        Role role = getRole(roleName);
        requireUser(userId);
        List<String> before = assignedRoleNames(userId);
        if (before.contains(roleName.name())) {
            throw new UserException(UserErrorCode.ROLE_ALREADY_ASSIGNED);
        }
        userRoleRepository.save(UserRole.create(userId, role.getId(), now));
        List<String> after = new ArrayList<>(before);
        after.add(roleName.name());
        after.sort(String::compareTo);
        messagePublisher.publish(new RoleChanged(userId, after, now));
        return new RoleAssignmentInfo(before, after);
    }

    /**
     * 역할 배정을 해제하고 전/후 역할명 스냅샷을 반환한다.
     *
     * @throws UserException 회원 미존재(404), 역할 미존재(404), 배정되지 않은 역할이면(404)
     */
    @Transactional
    public RoleAssignmentInfo revokeRole(UUID userId, RoleName roleName, Instant now) {
        Role role = getRole(roleName);
        requireUser(userId);
        UserRole assignment = userRoleRepository
                .findByUserIdAndRoleId(userId, role.getId())
                .orElseThrow(() -> new UserException(UserErrorCode.ROLE_NOT_ASSIGNED));
        List<String> before = assignedRoleNames(userId);
        userRoleRepository.delete(assignment);
        List<String> after = new ArrayList<>(before);
        after.remove(roleName.name());
        messagePublisher.publish(new RoleChanged(userId, after, now));
        return new RoleAssignmentInfo(before, after);
    }

    private Role getRole(RoleName roleName) {
        return roleRepository.findByName(roleName).orElseThrow(() -> new UserException(UserErrorCode.ROLE_NOT_FOUND));
    }

    private void requireUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    private List<String> assignedRoleNames(UUID userId) {
        List<UUID> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .toList();
        return roleRepository.findAllById(roleIds).stream()
                .map(role -> role.getName().name())
                .sorted()
                .toList();
    }
}
