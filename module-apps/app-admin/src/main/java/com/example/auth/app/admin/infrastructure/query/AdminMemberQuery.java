package com.example.auth.app.admin.infrastructure.query;

import com.example.auth.common.core.crypto.BlindIndexer;
import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import com.example.auth.domain.auth.repository.LoginAttemptRepository;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.entity.UserRole;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.RoleRepository;
import com.example.auth.domain.user.repository.UserRepository;
import com.example.auth.domain.user.repository.UserRoleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 검색/상세/로그인 이력의 크로스 스키마 read 전용 질의다 — admin 격리 구역(엔티티·리포지토리 접근
 * 허용, 읽기 전용). 결과는 마스킹 적용된 조회 레코드로 변환해 구역 밖으로 내보낸다.
 */
@Component
public class AdminMemberQuery {

    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final BlindIndexer blindIndexer;

    public AdminMemberQuery(
            UserRepository userRepository,
            AuthAccountRepository authAccountRepository,
            UserRoleRepository userRoleRepository,
            RoleRepository roleRepository,
            LoginAttemptRepository loginAttemptRepository,
            BlindIndexer blindIndexer) {
        this.userRepository = userRepository;
        this.authAccountRepository = authAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.blindIndexer = blindIndexer;
    }

    /**
     * 로그인 이메일 정확 일치로 검색한다(정규화 유니크 — 0..1건). 형식이 어긋난 입력은 저장 형식과 매치될
     * 수 없으므로 빈 결과로 취급한다.
     */
    @Transactional(readOnly = true)
    public List<MemberSummaryRow> searchByLoginEmail(String email) {
        Email parsed;
        try {
            parsed = Email.of(email);
        } catch (IllegalArgumentException invalidFormat) {
            return List.of();
        }
        return authAccountRepository
                .findByLoginEmail(parsed)
                .flatMap(account ->
                        userRepository.findById(account.getUserId()).map(user -> MemberSummaryRow.of(user, account)))
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * 전화 blind index 동등 일치로 검색한다(비유니크 — 재가입·번호 재할당으로 다건 가능). 입력은 저장
     * 형식과 같은 E.164여야 매치된다.
     */
    @Transactional(readOnly = true)
    public List<MemberSummaryRow> searchByPhone(String phone) {
        return userRepository.findByContactPhoneBidx(blindIndexer.blindIndex(phone)).stream()
                .map(user -> MemberSummaryRow.of(
                        user, authAccountRepository.findById(user.getId()).orElse(null)))
                .toList();
    }

    /**
     * usr(프로필·생명주기·역할 배정)와 auth(잠금·로그인 이메일)를 합성한 상세를 반환한다.
     *
     * @throws UserException 회원 미존재(404)
     * @throws AuthException 인증 계정 미존재(404)
     */
    @Transactional(readOnly = true)
    public MemberDetailRow getDetail(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        AuthAccount account = authAccountRepository
                .findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_NOT_FOUND));
        return MemberDetailRow.of(user, account, assignedRoleNames(userId));
    }

    /**
     * 로그인 이력을 최신순으로 페이지 조회한다.
     */
    @Transactional(readOnly = true)
    public Page<LoginAttemptRow> getLoginAttempts(UUID userId, int page, int size) {
        return loginAttemptRepository
                .findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "at")))
                .map(LoginAttemptRow::from);
    }

    /**
     * 회원 존재를 검증한다(명령 전 존재 검증용 — 강제 로그아웃 등).
     *
     * @throws UserException 회원 미존재(404)
     */
    @Transactional(readOnly = true)
    public void requireMember(UUID userId) {
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
