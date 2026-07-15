package com.example.auth.app.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.entity.RegistrationType;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.info.RegistrationCompletionInfo;
import com.example.auth.domain.auth.service.AccountRegistrationProcessor;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.info.IdentityVerifiedInfo;
import com.example.auth.domain.user.port.IdentityProviderRequest;
import com.example.auth.domain.user.service.IdentityVerificationProcessor;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 가입 커밋({@code CreateUser} 단일 크로스스키마 트랜잭션) 통합 검증: usr(User·ConsentRecord·
 * CiRegistry.link·IdentityVerification 연결)·auth(AuthAccount·PasswordCredential) 원자 생성, 중간 실패 시
 * 두 스키마 전량 롤백(고아 없음), CI·로그인 이메일 중복 거부, 멱등 재실행을 실 PostgreSQL로 확인한다.
 */
@SpringBootTest
@Testcontainers
class RegistrationCompletionIT {

    private static final List<ConsentSelection> CONSENTS = List.of(
            new ConsentSelection(TermsType.SERVICE.name(), 1),
            new ConsentSelection(TermsType.PRIVACY_REQUIRED.name(), 1),
            new ConsentSelection(TermsType.AGE14.name(), 1));

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private IdentityVerificationProcessor identityVerificationProcessor;

    @Autowired
    private AccountRegistrationProcessor accountRegistrationProcessor;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registersAllAggregatesAtomicallyAndReplaysIdempotently() {
        RegistrationCompletionInfo completion = completionFor("atomic@example.com", "+821055550001");

        UUID userId = accountRegistrationProcessor.register(completion, "secret123");

        assertThat(jdbc.queryForObject("select status from usr.users where id = ?", String.class, userId))
                .isEqualTo("ACTIVE");
        assertThat(count("select count(*) from usr.consent_record where user_id = ? and action = 'AGREE'", userId))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "select linked_user_id from usr.ci_registry where ci_hash = ? and status = 'ACTIVE_LINKED'",
                        UUID.class,
                        completion.ciHash()))
                .isEqualTo(userId);
        assertThat(jdbc.queryForObject(
                        "select user_id from usr.identity_verification where id = ?",
                        UUID.class,
                        completion.verificationRef()))
                .isEqualTo(userId);
        assertThat(count("select count(*) from auth.auth_account where user_id = ?", userId))
                .isEqualTo(1);
        assertThat(count("select count(*) from auth.password_credential where user_id = ?", userId))
                .isEqualTo(1);

        // 멱등 재실행(커밋 후 세션 소비 실패 창의 재요청) — 신규 쓰기 없이 같은 UserId를 재반환한다.
        UUID replayed = accountRegistrationProcessor.register(completion, "secret123");
        assertThat(replayed).isEqualTo(userId);
        assertThat(count("select count(*) from usr.users where ci_hash = ?", completion.ciHash()))
                .isEqualTo(1);
        assertThat(count("select count(*) from usr.consent_record where user_id = ?", userId))
                .isEqualTo(3);
        assertThat(count("select count(*) from auth.auth_account where user_id = ?", userId))
                .isEqualTo(1);
    }

    @Test
    void rollsBackBothSchemasWhenPasswordPolicyRejectsAtLastWrite() {
        RegistrationCompletionInfo completion = completionFor("rollback@example.com", "+821055550002");
        long consentRecordsBefore = count("select count(*) from usr.consent_record");

        // 자격증명 등록(트랜잭션의 마지막 쓰기)의 정책 재검증이 거부 — 그 앞의 usr 전체·AuthAccount까지
        // 전량 롤백돼야 한다.
        assertThatThrownBy(() -> accountRegistrationProcessor.register(completion, "short"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(AuthErrorCode.PASSWORD_POLICY_VIOLATION));

        assertThat(count("select count(*) from usr.users where ci_hash = ?", completion.ciHash()))
                .isZero();
        assertThat(count("select count(*) from usr.ci_registry where ci_hash = ?", completion.ciHash()))
                .isZero();
        assertThat(count("select count(*) from usr.consent_record")).isEqualTo(consentRecordsBefore);
        assertThat(count("select count(*) from auth.auth_account where login_email = ?", "rollback@example.com"))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select user_id from usr.identity_verification where id = ?",
                        UUID.class,
                        completion.verificationRef()))
                .isNull();
    }

    @Test
    void rejectsDuplicateCiAtCommit() {
        // 같은 정체성(=같은 CI)의 두 번들을 링크 전에 만들어 완료 시점 중복 판정만 겨냥한다(verify 시점
        // soft-check는 아직 통과하는 경합 시나리오).
        RegistrationCompletionInfo first = completionFor("ci-first@example.com", "+821055550003");
        RegistrationCompletionInfo second = completionFor("ci-second@example.com", "+821055550003");
        UUID userId = accountRegistrationProcessor.register(first, "secret123");

        assertThatThrownBy(() -> accountRegistrationProcessor.register(second, "secret123"))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.DUPLICATE_CI));

        assertThat(count("select count(*) from usr.users where ci_hash = ?", first.ciHash()))
                .isEqualTo(1);
        assertThat(count("select count(*) from auth.auth_account where login_email = ?", "ci-second@example.com"))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select user_id from usr.identity_verification where id = ?",
                        UUID.class,
                        second.verificationRef()))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "select linked_user_id from usr.ci_registry where ci_hash = ?", UUID.class, first.ciHash()))
                .isEqualTo(userId);
    }

    @Test
    void rejectsDuplicateLoginEmailAndRollsBackUserSchema() {
        String email = "dup-email@example.com";
        accountRegistrationProcessor.register(completionFor(email, "+821055550004"), "secret123");
        RegistrationCompletionInfo second = completionFor(email, "+821055550005");

        // usr 쓰기(User·동의·CI링크)까지 진행된 뒤 AuthAccount 유니크에서 거부 — usr까지 전량 롤백돼야 한다.
        assertThatThrownBy(() -> accountRegistrationProcessor.register(second, "secret123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e ->
                        assertThat(((AuthException) e).getErrorCode()).isEqualTo(AuthErrorCode.LOGIN_EMAIL_DUPLICATE));

        assertThat(count("select count(*) from usr.users where ci_hash = ?", second.ciHash()))
                .isZero();
        assertThat(count("select count(*) from usr.ci_registry where ci_hash = ?", second.ciHash()))
                .isZero();
        assertThat(count("select count(*) from auth.auth_account where login_email = ?", email))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select user_id from usr.identity_verification where id = ?",
                        UUID.class,
                        second.verificationRef()))
                .isNull();
    }

    private RegistrationCompletionInfo completionFor(String loginEmail, String phone) {
        IdentityVerifiedInfo verified = identityVerificationProcessor.verify(
                new IdentityProviderRequest("완료테스터", LocalDate.of(1991, 2, 3), Gender.FEMALE, Carrier.KT, phone));
        return new RegistrationCompletionInfo(
                UuidV7Generator.generate(),
                RegistrationType.LOCAL,
                loginEmail,
                verified.verificationId(),
                verified.ciHash(),
                CONSENTS,
                null);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
