package com.example.auth.domain.auth.service;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.entity.Email;
import com.example.auth.domain.auth.entity.LifecycleStatus;
import com.example.auth.domain.auth.event.LoginEmailChanged;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 계정 단일 애그리거트 전이를 담당한다.
 */
@Service
public class AuthAccountModifier {

    private static final Logger log = LoggerFactory.getLogger(AuthAccountModifier.class);

    private final AuthAccountRepository repository;
    private final MessagePublisher messagePublisher;

    public AuthAccountModifier(AuthAccountRepository repository, MessagePublisher messagePublisher) {
        this.repository = repository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 로그인 식별자를 변경한다(정규화 후 유니크 — 연락용 이메일과 독립). 변경 사실을 보안 이벤트로 발행한다.
     *
     * @throws AuthException 미존재 계정이면(404), 이미 사용 중인 이메일이면(409)
     */
    @Transactional
    public void changeLoginEmail(UUID userId, String newEmail) {
        Email email = Email.of(newEmail);
        AuthAccount account =
                repository.findById(userId).orElseThrow(() -> new AuthException(AuthErrorCode.ACCOUNT_NOT_FOUND));
        if (email.equals(account.getLoginEmail())) {
            return;
        }
        if (repository.existsByLoginEmail(email)) {
            throw new AuthException(AuthErrorCode.LOGIN_EMAIL_DUPLICATE);
        }
        account.changeLoginEmail(email);
        messagePublisher.publish(new LoginEmailChanged(userId, Instant.now()));
    }

    /**
     * 유저 역할 배정 스냅샷을 반영한다 — {@code occurredAt} 단조 기준 멱등이며 역순·중복 이벤트는
     * 무시된다. 계정 미존재도 무시한다(at-least-once 재전달의 안전 흡수).
     */
    @Transactional
    public void applyRoles(UUID userId, List<String> roles, Instant occurredAt) {
        AuthAccount account = repository.findById(userId).orElse(null);
        if (account == null) {
            log.warn("역할 투영 반영 대상 계정 없음 userId={} roles={}", userId, roles);
            return;
        }
        account.applyRoles(roles, occurredAt);
    }

    /**
     * 유저 생명주기 스냅샷을 반영한다 — 단조 버전 기준 멱등이며 역순·중복 이벤트는 무시된다.
     * 계정 미존재도 무시한다(at-least-once 재전달이 계정 삭제 뒤 도착하는 경우의 안전 흡수).
     */
    @Transactional
    public void applyUserStatus(UUID userId, LifecycleStatus status, long version) {
        AuthAccount account = repository.findById(userId).orElse(null);
        if (account == null) {
            log.warn("유저 상태 스냅샷 반영 대상 계정 없음 userId={} status={} version={}", userId, status, version);
            return;
        }
        account.applyUserStatus(status, version);
    }
}
