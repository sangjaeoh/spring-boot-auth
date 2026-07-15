package com.example.auth.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.common.core.crypto.BlindIndexer;
import com.example.auth.common.core.crypto.EnvelopeCipher;
import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.CiRegistry;
import com.example.auth.domain.user.entity.ConsentAction;
import com.example.auth.domain.user.entity.ConsentRecord;
import com.example.auth.domain.user.entity.Contact;
import com.example.auth.domain.user.entity.Email;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.IdentityVerification;
import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.PhoneNumber;
import com.example.auth.domain.user.entity.Profile;
import com.example.auth.domain.user.entity.Provider;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.entity.VerificationResult;
import com.example.auth.domain.user.entity.VerificationStatus;
import com.example.auth.domain.user.event.UserStatusChanged;
import com.example.auth.domain.user.repository.CiRegistryRepository;
import com.example.auth.domain.user.repository.ConsentRecordRepository;
import com.example.auth.domain.user.repository.ConsentStateRepository;
import com.example.auth.domain.user.repository.IdentityVerificationRepository;
import com.example.auth.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 휴면·보존 파기 IT: 사전통지·전환 경계값(12개월·30일), EXPIRED 스윕(FAILED 오분류 금지), 본인인증 결과
 * PII 파기창(5일), CI tombstone 보존창(6개월), 탈퇴 후 동의이력 보존창(5년)을 실 PostgreSQL에 대해
 * 검증한다.
 */
@SpringBootTest
@Import({JpaConfig.class, DormancyRetentionIT.TestBeans.class})
@Testcontainers
class DormancyRetentionIT {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private DormancyProcessor dormancyProcessor;

    @Autowired
    private UserRetentionRemover retentionRemover;

    @Autowired
    private UserAppender userAppender;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityVerificationRepository identityVerificationRepository;

    @Autowired
    private CiRegistryRepository ciRegistryRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    @Autowired
    private ConsentStateRepository consentStateRepository;

    @Autowired
    private RecordingPublisher publisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void noticesOnlyUsersPastNoticeThreshold() {
        UUID dueUserId = newUser("notice-due");
        UUID freshUserId = newUser("notice-fresh");
        // 미접속 12개월−30일 경계: 지난 사람만 통지 대상.
        mutate(dueUserId, user -> user.recordLogin(monthsAgo(11).minusSeconds(86_400)));
        mutate(freshUserId, user -> user.recordLogin(monthsAgo(10)));

        int notified = dormancyProcessor.notifyUpcoming(NOW);

        assertThat(notified).isEqualTo(1);
        assertThat(load(dueUserId).getDormancyNotifiedAt()).isEqualTo(NOW);
        assertThat(load(freshUserId).getDormancyNotifiedAt()).isNull();
    }

    @Test
    void transitionsOnlyAfterInactivityAndNoticeAging() {
        UUID dueUserId = newUser("transition-due");
        UUID noticedRecentlyId = newUser("transition-notice-young");
        UUID activeRecentlyId = newUser("transition-recent-login");
        mutate(dueUserId, user -> {
            user.recordLogin(monthsAgo(13));
            user.markDormancyNotified(NOW.minusSeconds(31L * 86_400));
        });
        // 사전통지 후 30일이 지나지 않은 회원은 전환하지 않는다(통지 숙려 보장).
        mutate(noticedRecentlyId, user -> {
            user.recordLogin(monthsAgo(13));
            user.markDormancyNotified(NOW.minusSeconds(10L * 86_400));
        });
        // 미접속 12개월이 지나지 않은 회원은 통지가 오래됐어도 전환하지 않는다.
        mutate(activeRecentlyId, user -> {
            user.recordLogin(monthsAgo(11));
            user.markDormancyNotified(NOW.minusSeconds(31L * 86_400));
        });

        List<UUID> transitioned = dormancyProcessor.transitionDue(NOW);

        assertThat(transitioned).containsExactly(dueUserId);
        User dormant = load(dueUserId);
        assertThat(dormant.getStatus()).isEqualTo(LifecycleStatus.DORMANT);
        assertThat(dormant.getStatusVersion()).isEqualTo(1);
        assertThat(publisher.eventsOf(UserStatusChanged.class)).anySatisfy(event -> {
            assertThat(event.userId()).isEqualTo(dueUserId);
            assertThat(event.status()).isEqualTo(LifecycleStatus.DORMANT);
            assertThat(event.statusVersion()).isEqualTo(1);
        });
        assertThat(load(noticedRecentlyId).getStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(load(activeRecentlyId).getStatus()).isEqualTo(LifecycleStatus.ACTIVE);
    }

    @Test
    void reactivateRestoresActiveAndPublishesMonotonicVersion() {
        UUID userId = newUser("reactivate");
        mutate(userId, user -> {
            user.recordLogin(monthsAgo(13));
            user.markDormancyNotified(NOW.minusSeconds(31L * 86_400));
        });
        dormancyProcessor.transitionDue(NOW);

        long version = dormancyProcessor.reactivate(userId, NOW.plusSeconds(60));

        assertThat(version).isEqualTo(2);
        User user = load(userId);
        assertThat(user.getStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(user.getLastLoginAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void expiresStaleRequestedVerificationsWithoutMisclassifyingAsFailed() {
        IdentityVerification stale =
                IdentityVerification.create(Provider.MOCK, NOW.minusSeconds(7_200), NOW.minusSeconds(3_600));
        IdentityVerification live = IdentityVerification.create(Provider.MOCK, NOW, NOW.plusSeconds(3_600));
        identityVerificationRepository.save(stale);
        identityVerificationRepository.save(live);

        int expired = retentionRemover.expireStaleVerifications(NOW);

        assertThat(expired).isEqualTo(1);
        assertThat(identityVerificationRepository
                        .findById(stale.getId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(VerificationStatus.EXPIRED);
        assertThat(identityVerificationRepository
                        .findById(live.getId())
                        .orElseThrow()
                        .getStatus())
                .isEqualTo(VerificationStatus.REQUESTED);
    }

    @Test
    void purgesVerificationResultPiiPastWindowOnly() {
        IdentityVerification old = verifiedAt(NOW.minusSeconds(6L * 86_400), "old-ci");
        IdentityVerification recent = verifiedAt(NOW.minusSeconds(4L * 86_400), "recent-ci");

        int purged = retentionRemover.purgeStaleVerificationResults(NOW);

        assertThat(purged).isEqualTo(1);
        assertThat(identityVerificationRepository
                        .findById(old.getId())
                        .orElseThrow()
                        .getResult())
                .isNull();
        assertThat(identityVerificationRepository
                        .findById(recent.getId())
                        .orElseThrow()
                        .getResult())
                .isNotNull();
    }

    @Test
    void purgesCiTombstonesPastRetentionWindowOnly() {
        CiRegistry expired = tombstone("tomb-old", monthsAgo(7));
        CiRegistry retained = tombstone("tomb-recent", monthsAgo(5));

        int purged = retentionRemover.purgeExpiredCiTombstones(NOW);

        assertThat(purged).isEqualTo(1);
        assertThat(ciRegistryRepository.findById(expired.getId())).isEmpty();
        assertThat(ciRegistryRepository.findById(retained.getId())).isPresent();
    }

    @Test
    void purgesConsentLogOnlyAfterFiveYearsSinceWithdrawal() {
        UUID oldWithdrawn = newUser("consent-old");
        UUID recentWithdrawn = newUser("consent-recent");
        consentRecordRepository.save(
                ConsentRecord.create(oldWithdrawn, TermsType.SERVICE, 1, ConsentAction.AGREE, null, NOW));
        consentRecordRepository.save(
                ConsentRecord.create(recentWithdrawn, TermsType.SERVICE, 1, ConsentAction.AGREE, null, NOW));
        mutate(oldWithdrawn, user -> user.withdraw(yearsAgo(6)));
        mutate(recentWithdrawn, user -> user.withdraw(yearsAgo(4)));

        int purged = retentionRemover.purgeExpiredWithdrawnConsents(NOW);

        assertThat(purged).isEqualTo(1);
        assertThat(consentRecordRepository.findAll().stream()
                        .filter(record -> oldWithdrawn.equals(record.getUserId()))
                        .count())
                .isZero();
        assertThat(consentRecordRepository.findAll().stream()
                        .filter(record -> recentWithdrawn.equals(record.getUserId()))
                        .count())
                .isEqualTo(1);
    }

    private UUID newUser(String seed) {
        Profile profile = Profile.of("홍길동", java.time.LocalDate.of(1990, 1, 1), Gender.MALE);
        Contact contact = Contact.of(
                Email.of(seed + "@example.com"),
                PhoneNumber.of(Carrier.SKT, "+8210%08d".formatted(Math.floorMod(seed.hashCode(), 100_000_000))));
        return userAppender.register(profile, contact, "ci-" + seed);
    }

    private IdentityVerification verifiedAt(Instant requestedAt, String ci) {
        IdentityVerification verification =
                IdentityVerification.create(Provider.MOCK, requestedAt, requestedAt.plusSeconds(600));
        verification.complete(
                VerificationResult.of(
                        "홍길동",
                        java.time.LocalDate.of(1990, 1, 1),
                        Gender.MALE,
                        Carrier.SKT,
                        "+821012345678",
                        ci,
                        "di-" + ci),
                requestedAt.plusSeconds(60));
        return identityVerificationRepository.save(verification);
    }

    private CiRegistry tombstone(String seed, Instant withdrawnAt) {
        CiRegistry registry = CiRegistry.link("ci-" + seed, UUID.randomUUID(), withdrawnAt.minusSeconds(86_400));
        registry.retainOnWithdrawal(withdrawnAt);
        return ciRegistryRepository.save(registry);
    }

    private void mutate(UUID userId, Consumer<User> mutation) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            mutation.accept(user);
        });
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    private static Instant monthsAgo(int months) {
        return ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMonths(months).toInstant();
    }

    private static Instant yearsAgo(int years) {
        return ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC).minusYears(years).toInstant();
    }

    /**
     * 검증 대상 조립. PII 컨버터가 있는 엔티티들이 함께 스캔되므로 {@code EnvelopeCipher} 빈이 필요하다.
     */
    @TestConfiguration
    static class TestBeans {

        @Bean
        EnvelopeCipher envelopeCipher() {
            return new EnvelopeCipher() {
                @Override
                public String encrypt(String plaintext) {
                    return "enc1:" + Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String decrypt(String ciphertext) {
                    return new String(
                            Base64.getDecoder().decode(ciphertext.substring("enc1:".length())), StandardCharsets.UTF_8);
                }
            };
        }

        @Bean
        BlindIndexer blindIndexer() {
            return value -> "1:" + Integer.toHexString(value.hashCode());
        }

        @Bean
        RecordingPublisher messagePublisher() {
            return new RecordingPublisher();
        }

        @Bean
        UserAppender userAppender(UserRepository repository, BlindIndexer blindIndexer) {
            return new UserAppender(repository, blindIndexer);
        }

        @Bean
        DormancyProcessor dormancyProcessor(UserRepository userRepository, RecordingPublisher publisher) {
            return new DormancyProcessor(userRepository, publisher, 12, 30);
        }

        @Bean
        UserRetentionRemover userRetentionRemover(
                IdentityVerificationRepository identityVerificationRepository,
                CiRegistryRepository ciRegistryRepository,
                UserRepository userRepository,
                ConsentRecordRepository consentRecordRepository,
                ConsentStateRepository consentStateRepository) {
            return new UserRetentionRemover(
                    identityVerificationRepository,
                    ciRegistryRepository,
                    userRepository,
                    consentRecordRepository,
                    consentStateRepository,
                    5,
                    6,
                    5);
        }
    }

    static class RecordingPublisher implements MessagePublisher {

        private final List<IntegrationEvent> events = new ArrayList<>();

        @Override
        public void publish(IntegrationEvent event) {
            events.add(event);
        }

        <E extends IntegrationEvent> List<E> eventsOf(Class<E> type) {
            return events.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }
}
