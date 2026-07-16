package com.example.auth.app.admin;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.app.admin.presentation.v1.AdminMemberDetailResponse;
import com.example.auth.app.admin.presentation.v1.AdminMemberSummaryResponse;
import com.example.auth.app.admin.presentation.v1.RoleAssignRequest;
import com.example.auth.common.auth.jwt.JwtIssuer;
import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.entity.RegistrationType;
import com.example.auth.domain.auth.info.RegistrationCompletionInfo;
import com.example.auth.domain.auth.info.SessionCreated;
import com.example.auth.domain.auth.service.AccountRegistrationProcessor;
import com.example.auth.domain.auth.service.AuthAccountReader;
import com.example.auth.domain.auth.service.SessionProcessor;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.RoleName;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.info.IdentityVerifiedInfo;
import com.example.auth.domain.user.port.IdentityProviderRequest;
import com.example.auth.domain.user.service.IdentityVerificationProcessor;
import com.example.auth.domain.user.service.RoleAssignmentProcessor;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 관리자 API E2E: 역할 가드(미인증 401·비관리자 403·관리자 성공), 검색 마스킹, 강제 로그아웃 즉시 401,
 * ADMIN_LOCKED 잠금/해제와 실효 상태 렌더, SUPER_ADMIN 전용 권한 변경 → RoleChanged 소비로 인증 roles
 * 투영 갱신, 관리자 행위의 전/후 값 감사 기록을 실 인프라로 검증한다.
 *
 * <p>가입 시드는 app-api의 프로비저닝과 동일한 정식 커밋 경로(Mock 본인인증 → CreateUser)를 태우고,
 * 토큰은 이 앱의 키링으로 직접 발급한다(관리자 앱은 로그인 라우트를 노출하지 않는다).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class AdminApiE2EIT {

    private static final List<ConsentSelection> SEED_CONSENTS = List.of(
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
    private TestRestTemplate rest;

    @Autowired
    private IdentityVerificationProcessor identityVerificationProcessor;

    @Autowired
    private AccountRegistrationProcessor accountRegistrationProcessor;

    @Autowired
    private RoleAssignmentProcessor roleAssignmentProcessor;

    @Autowired
    private SessionProcessor sessionProcessor;

    @Autowired
    private AuthAccountReader authAccountReader;

    @Autowired
    private JwtIssuer jwtIssuer;

    @Test
    void adminApiEnforcesRoleGuardAndMasksSearchResults() {
        String memberEmail = "guard-member@example.com";
        UUID memberId = provision(memberEmail);
        UUID adminId = provisionWithRole("guard-admin@example.com", RoleName.ADMIN);

        // 미인증 → 401.
        assertThat(rest.getForEntity("/admin/members?email=" + memberEmail, String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);

        // 비관리자(USER) 인증 → 403.
        String memberToken = accessTokenFor(memberId);
        assertThat(get("/admin/members?email=" + memberEmail, memberToken)
                        .getStatusCode()
                        .value())
                .isEqualTo(403);

        // 관리자 → 200 + 마스킹(이름·로그인 이메일 원문 비노출).
        String adminToken = accessTokenFor(adminId);
        ResponseEntity<AdminMemberSummaryResponse[]> found = rest.exchange(
                "/admin/members?email=" + memberEmail,
                HttpMethod.GET,
                bearer(adminToken),
                AdminMemberSummaryResponse[].class);
        assertThat(found.getStatusCode().value()).isEqualTo(200);
        AdminMemberSummaryResponse[] rows = requireNonNull(found.getBody());
        assertThat(rows).hasSize(1);
        assertThat(rows[0].userId()).isEqualTo(memberId);
        assertThat(rows[0].maskedName()).isEqualTo("시****");
        assertThat(rows[0].maskedLoginEmail()).isEqualTo("gu***@example.com");
        assertThat(rows[0].effectiveStatus()).isEqualTo("ACTIVE");

        // 검색 조건 미제시 → 400.
        assertThat(get("/admin/members", adminToken).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void searchesByPhoneBlindIndex() {
        String memberEmail = "phone-member@example.com";
        UUID memberId = provision(memberEmail);
        UUID adminId = provisionWithRole("phone-admin@example.com", RoleName.ADMIN);

        // '+'는 쿼리에서 공백으로 디코드되므로 템플릿 확장 없이 직접 인코딩한 절대 URI로 호출한다.
        URI uri = URI.create(rest.getRootUri()
                + "/admin/members?phone="
                + URLEncoder.encode(seedPhone(memberEmail), StandardCharsets.UTF_8));
        ResponseEntity<AdminMemberSummaryResponse[]> found =
                rest.exchange(uri, HttpMethod.GET, bearer(accessTokenFor(adminId)), AdminMemberSummaryResponse[].class);

        assertThat(found.getStatusCode().value()).isEqualTo(200);
        assertThat(requireNonNull(found.getBody()))
                .extracting(AdminMemberSummaryResponse::userId)
                .containsExactly(memberId);
    }

    @Test
    void forceLogoutRevokesMemberSessionsImmediately() {
        UUID memberId = provision("logout-member@example.com");
        UUID adminId = provisionWithRole("logout-admin@example.com", RoleName.ADMIN);
        String memberToken = accessTokenFor(memberId);
        String adminToken = accessTokenFor(adminId);

        // 강제 로그아웃 전: 세션이 살아 있어 인증은 성립하고 역할 부족 403이다.
        assertThat(get("/admin/members/" + memberId, memberToken)
                        .getStatusCode()
                        .value())
                .isEqualTo(403);

        ResponseEntity<Void> forced = rest.exchange(
                "/admin/members/" + memberId + "/force-logout", HttpMethod.POST, bearer(adminToken), Void.class);
        assertThat(forced.getStatusCode().value()).isEqualTo(204);

        // 소비자가 전 세션을 전멸시켜 기존 Access가 즉시 401이 된다(서명은 유효해도 세션 무효).
        assertThat(get("/admin/members/" + memberId, memberToken)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);

        // 행위가 감사에 남는다(행위자 = 관리자).
        assertThat(auditActions(adminToken, memberId)).contains("admin.member.force-logout");
    }

    @Test
    void adminLockAndUnlockTransitionWithBeforeAfterAudit() {
        UUID memberId = provision("lock-member@example.com");
        UUID adminId = provisionWithRole("lock-admin@example.com", RoleName.ADMIN);
        String adminToken = accessTokenFor(adminId);
        String lockUrl = "/admin/members/" + memberId + "/lock";

        assertThat(rest.exchange(lockUrl, HttpMethod.POST, bearer(adminToken), Void.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(204);
        AdminMemberDetailResponse locked = detail(adminToken, memberId);
        assertThat(locked.lockState()).isEqualTo("ADMIN_LOCKED");
        assertThat(locked.effectiveStatus()).isEqualTo("LOCKED");
        assertThat(locked.lifecycleStatus()).isEqualTo("ACTIVE");

        // 이미 잠금 → 409.
        assertThat(rest.exchange(lockUrl, HttpMethod.POST, bearer(adminToken), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(409);

        assertThat(rest.exchange(lockUrl, HttpMethod.DELETE, bearer(adminToken), Void.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(204);
        AdminMemberDetailResponse unlocked = detail(adminToken, memberId);
        assertThat(unlocked.lockState()).isEqualTo("NONE");
        assertThat(unlocked.effectiveStatus()).isEqualTo("ACTIVE");

        // 잠기지 않음 → 409.
        assertThat(rest.exchange(lockUrl, HttpMethod.DELETE, bearer(adminToken), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(409);

        // 전/후 값이 감사에 남는다.
        List<AuditEntry> entries = auditEntries(adminToken, memberId);
        AuditEntry lockEntry = entries.stream()
                .filter(entry -> "admin.member.lock".equals(entry.action()))
                .findFirst()
                .orElseThrow();
        assertThat(lockEntry.actor()).isEqualTo(adminId.toString());
        assertThat(lockEntry.beforeValue()).contains("NONE");
        assertThat(lockEntry.afterValue()).contains("ADMIN_LOCKED");
        AuditEntry unlockEntry = entries.stream()
                .filter(entry -> "admin.member.unlock".equals(entry.action()))
                .findFirst()
                .orElseThrow();
        assertThat(unlockEntry.beforeValue()).contains("ADMIN_LOCKED");
        assertThat(unlockEntry.afterValue()).contains("NONE");
    }

    @Test
    void roleChangeRequiresSuperAdminAndUpdatesClaimProjection() {
        UUID memberId = provision("role-member@example.com");
        UUID adminId = provisionWithRole("role-admin@example.com", RoleName.ADMIN);
        UUID superAdminId = provisionWithRole("role-super@example.com", RoleName.SUPER_ADMIN);
        String rolesUrl = "/admin/members/" + memberId + "/roles";

        // ADMIN은 권한 변경 불가(SUPER_ADMIN 메서드 가드) → 403.
        assertThat(post(rolesUrl, accessTokenFor(adminId), new RoleAssignRequest("ADMIN"))
                        .getStatusCode()
                        .value())
                .isEqualTo(403);

        String superToken = accessTokenFor(superAdminId);
        assertThat(post(rolesUrl, superToken, new RoleAssignRequest("ADMIN"))
                        .getStatusCode()
                        .value())
                .isEqualTo(204);

        // RoleChanged 소비가 인증 roles 투영(토큰 클레임의 원천)을 갱신한다 — 다음 발급 토큰부터 반영.
        assertThat(authAccountReader.getRoles(memberId)).containsExactly("ADMIN", "USER");
        assertThat(detail(superToken, memberId).roles()).containsExactly("ADMIN", "USER");

        // 중복 배정 → 409, 미존재 역할 → 404.
        assertThat(post(rolesUrl, superToken, new RoleAssignRequest("ADMIN"))
                        .getStatusCode()
                        .value())
                .isEqualTo(409);
        assertThat(post(rolesUrl, superToken, new RoleAssignRequest("NOPE"))
                        .getStatusCode()
                        .value())
                .isEqualTo(404);

        // 해제 → 투영이 USER로 되돌아간다. 미배정 해제 → 404.
        assertThat(rest.exchange(rolesUrl + "/ADMIN", HttpMethod.DELETE, bearer(superToken), Void.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(204);
        assertThat(authAccountReader.getRoles(memberId)).containsExactly("USER");
        assertThat(rest.exchange(rolesUrl + "/ADMIN", HttpMethod.DELETE, bearer(superToken), String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);

        // 전/후 역할 스냅샷이 감사에 남는다.
        AuditEntry assignEntry = auditEntries(superToken, memberId).stream()
                .filter(entry -> "admin.member.role-assign".equals(entry.action()))
                .findFirst()
                .orElseThrow();
        assertThat(assignEntry.actor()).isEqualTo(superAdminId.toString());
        assertThat(assignEntry.beforeValue()).isEqualTo("{\"roles\":[\"USER\"]}");
        assertThat(assignEntry.afterValue()).isEqualTo("{\"roles\":[\"ADMIN\",\"USER\"]}");
    }

    private UUID provision(String email) {
        IdentityVerifiedInfo verified = identityVerificationProcessor.verify(new IdentityProviderRequest(
                "시드사용자", LocalDate.of(1990, 1, 1), Gender.MALE, Carrier.SKT, seedPhone(email)));
        RegistrationCompletionInfo completion = new RegistrationCompletionInfo(
                UuidV7Generator.generate(),
                RegistrationType.LOCAL,
                email,
                verified.verificationId(),
                verified.ciHash(),
                SEED_CONSENTS,
                null);
        return accountRegistrationProcessor.register(completion, "secret123");
    }

    private UUID provisionWithRole(String email, RoleName role) {
        UUID userId = provision(email);
        roleAssignmentProcessor.assignRole(userId, role, Instant.now());
        return userId;
    }

    /**
     * 이 앱의 키링으로 현재 roles 투영을 실은 Access 토큰을 발급한다(세션은 공유 Redis에 실생성).
     */
    private String accessTokenFor(UUID userId) {
        Instant now = Instant.now();
        SessionCreated session =
                sessionProcessor.createSession(userId, UuidV7Generator.generate(), "127.0.0.1", "junit", now);
        return jwtIssuer.issueAccess(
                userId, session.sessionId(), authAccountReader.getRoles(userId), Duration.ofMinutes(15), now);
    }

    private AdminMemberDetailResponse detail(String token, UUID userId) {
        ResponseEntity<AdminMemberDetailResponse> response = rest.exchange(
                "/admin/members/" + userId, HttpMethod.GET, bearer(token), AdminMemberDetailResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody());
    }

    private List<String> auditActions(String token, UUID target) {
        return auditEntries(token, target).stream().map(AuditEntry::action).toList();
    }

    private List<AuditEntry> auditEntries(String token, UUID target) {
        ResponseEntity<AuditPage> response = rest.exchange(
                "/admin/audit-logs?target=" + target + "&size=50", HttpMethod.GET, bearer(token), AuditPage.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return requireNonNull(response.getBody()).content();
    }

    private ResponseEntity<String> get(String url, String token) {
        return rest.exchange(url, HttpMethod.GET, bearer(token), String.class);
    }

    private ResponseEntity<String> post(String url, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private static String seedPhone(String email) {
        return "+8210%08d".formatted(Math.floorMod(email.hashCode(), 100_000_000));
    }

    private record AuditEntry(String actor, String action, String target, String beforeValue, String afterValue) {}

    private record AuditPage(List<AuditEntry> content) {}
}
