package com.example.auth.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.crypto.BlindIndexer;
import com.example.auth.common.core.crypto.EnvelopeCipher;
import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.CiRegistry;
import com.example.auth.domain.user.entity.CiStatus;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.IdentityVerification;
import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.Provider;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.entity.VerificationResult;
import com.example.auth.domain.user.event.UserWithdrawn;
import com.example.auth.domain.user.exception.RejoinCooldownException;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.CiRegistryRepository;
import com.example.auth.domain.user.repository.ConsentRecordRepository;
import com.example.auth.domain.user.repository.ConsentStateRepository;
import com.example.auth.domain.user.repository.IdentityVerificationRepository;
import com.example.auth.domain.user.repository.NotificationPreferenceRepository;
import com.example.auth.domain.user.repository.TermsDocumentRepository;
import com.example.auth.domain.user.repository.TermsVersionRepository;
import com.example.auth.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 탈퇴·재가입 쿨다운 IT: 탈퇴의 PII 즉시 파기 + CI tombstone 원자성, 쿨다운 내 재가입 거부(잔여일),
 * 쿨다운 경과 후 tombstone 재연결(CreateUser link 정합)을 실 PostgreSQL에 대해 검증한다.
 */
@SpringBootTest
@Import({JpaConfig.class, WithdrawalAndRejoinIT.TestBeans.class})
@Testcontainers
class WithdrawalAndRejoinIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserRegistrationProcessor registrationProcessor;

    @Autowired
    private UserWithdrawalProcessor withdrawalProcessor;

    @Autowired
    private IdentityVerificationRepository identityVerificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CiRegistryRepository ciRegistryRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    private CiUniquenessValidator ciUniquenessValidator;

    @Autowired
    private RecordingPublisher publisher;

    @Test
    void withdrawalPurgesPiiRetainsTombstoneAndKeepsConsentLog() {
        UUID userId = register("withdraw-1");

        long version = withdrawalProcessor.withdraw(userId);

        assertThat(version).isEqualTo(1);
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(LifecycleStatus.WITHDRAWN);
        assertThat(user.getProfile()).isNull();
        assertThat(user.getContact()).isNull();
        assertThat(user.getContactPhoneBidx()).isNull();
        assertThat(user.getCiHash()).isNull();
        assertThat(user.getWithdrawnAt()).isNotNull();

        CiRegistry registry =
                ciRegistryRepository.findByCiHash(ciHashOf("withdraw-1")).orElseThrow();
        assertThat(registry.getStatus()).isEqualTo(CiStatus.WITHDRAWN_RETAINED);
        assertThat(registry.getLinkedUserId()).isNull();
        assertThat(registry.getWithdrawnAt()).isNotNull();

        // 본인인증 결과 PII는 파기, 요청 사실은 유지.
        for (IdentityVerification verification : identityVerificationRepository.findByUserId(userId)) {
            assertThat(verification.getResult()).isNull();
        }
        // 법정 보존분(동의 이력)은 남는다. 수신 설정은 파기된다.
        assertThat(consentRecordRepository.findAll().stream()
                        .filter(record -> userId.equals(record.getUserId()))
                        .count())
                .isPositive();
        assertThat(notificationPreferenceRepository.findById(userId)).isEmpty();
        assertThat(publisher.eventsOf(UserWithdrawn.class))
                .anySatisfy(event -> assertThat(event.userId()).isEqualTo(userId));
    }

    @Test
    void rejectsDoubleWithdrawal() {
        UUID userId = register("withdraw-2");
        withdrawalProcessor.withdraw(userId);

        assertThatThrownBy(() -> withdrawalProcessor.withdraw(userId))
                .isInstanceOf(UserException.class)
                .satisfies(e ->
                        assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.USER_ALREADY_WITHDRAWN));
    }

    @Test
    void rejectsRejoinWithinCooldownWithRemainingDays() {
        UUID userId = register("cooldown-1");
        withdrawalProcessor.withdraw(userId);

        assertThatThrownBy(() -> ciUniquenessValidator.checkAvailable(ciHashOf("cooldown-1")))
                .isInstanceOf(RejoinCooldownException.class)
                .satisfies(e -> {
                    RejoinCooldownException cooldown = (RejoinCooldownException) e;
                    assertThat(cooldown.getErrorCode()).isEqualTo(UserErrorCode.REJOIN_COOLDOWN);
                    assertThat(cooldown.properties().get("remainingDays")).isEqualTo(30L);
                });
    }

    @Test
    void relinksTombstoneAfterCooldownOnFreshRegistration() {
        UUID firstUserId = register("cooldown-2");
        withdrawalProcessor.withdraw(firstUserId);

        // 쿨다운 경과를 시뮬레이션 — tombstone의 withdrawnAt을 31일 전으로 되돌린다.
        CiRegistry registry =
                ciRegistryRepository.findByCiHash(ciHashOf("cooldown-2")).orElseThrow();
        UUID freshUserId = registerAfterBackdating(registry, "cooldown-2");

        assertThat(freshUserId).isNotEqualTo(firstUserId);
        CiRegistry relinked =
                ciRegistryRepository.findByCiHash(ciHashOf("cooldown-2")).orElseThrow();
        assertThat(relinked.getStatus()).isEqualTo(CiStatus.ACTIVE_LINKED);
        assertThat(relinked.getLinkedUserId()).isEqualTo(freshUserId);
        assertThat(relinked.getWithdrawnAt()).isNull();
        // 이력 미승계 fresh 계정 — 원장 행은 재사용하되 회원은 새로 태어난다.
        assertThat(userRepository.findById(firstUserId).orElseThrow().getStatus())
                .isEqualTo(LifecycleStatus.WITHDRAWN);
    }

    private UUID registerAfterBackdating(CiRegistry registry, String seed) {
        // 테스트 전용 시간 이동: 도메인 전이 메서드로 31일 전 탈퇴를 재구성한다(setter 없음).
        UUID placeholder = registry.getLinkedUserId();
        assertThat(placeholder).isNull();
        registry.relink(UUID.randomUUID());
        registry.retainOnWithdrawal(Instant.now().minus(Duration.ofDays(31)));
        ciRegistryRepository.save(registry);
        return register(seed);
    }

    private UUID register(String seed) {
        UUID verificationId = createVerifiedIdentity(seed);
        return registrationProcessor.register(
                verificationId,
                ciHashOf(seed),
                seed + "@example.com",
                Map.of(TermsType.SERVICE, 1, TermsType.PRIVACY_REQUIRED, 1, TermsType.AGE14, 1));
    }

    private UUID createVerifiedIdentity(String seed) {
        IdentityVerification verification = IdentityVerification.create(
                Provider.MOCK, Instant.now(), Instant.now().plusSeconds(600));
        verification.complete(
                VerificationResult.of(
                        "홍길동",
                        LocalDate.of(1990, 1, 1),
                        Gender.MALE,
                        Carrier.SKT,
                        "+821012345678",
                        ciHashOf(seed),
                        "di-" + seed),
                Instant.now());
        identityVerificationRepository.save(verification);
        return verification.getId();
    }

    private static String ciHashOf(String seed) {
        return "1:" + seed;
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
        ConsentValidator consentValidator(
                TermsDocumentRepository documentRepository, TermsVersionRepository versionRepository) {
            return new ConsentValidator(documentRepository, versionRepository);
        }

        @Bean
        CiUniquenessValidator ciUniquenessValidator(CiRegistryRepository repository) {
            return new CiUniquenessValidator(repository, 30);
        }

        @Bean
        UserAppender userAppender(UserRepository repository, BlindIndexer blindIndexer) {
            return new UserAppender(repository, blindIndexer);
        }

        @Bean
        ConsentAppender consentAppender(
                ConsentRecordRepository consentRecordRepository, ConsentStateRepository consentStateRepository) {
            return new ConsentAppender(consentRecordRepository, consentStateRepository);
        }

        @Bean
        NotificationPreferenceAppender notificationPreferenceAppender(NotificationPreferenceRepository repository) {
            return new NotificationPreferenceAppender(repository);
        }

        @Bean
        NotificationPreferenceRemover notificationPreferenceRemover(NotificationPreferenceRepository repository) {
            return new NotificationPreferenceRemover(repository);
        }

        @Bean
        UserRegistrationProcessor userRegistrationProcessor(
                IdentityVerificationRepository identityVerificationRepository,
                ConsentValidator consentValidator,
                CiUniquenessValidator ciUniquenessValidator,
                UserAppender userAppender,
                ConsentAppender consentAppender,
                NotificationPreferenceAppender notificationPreferenceAppender,
                CiRegistryRepository ciRegistryRepository) {
            return new UserRegistrationProcessor(
                    identityVerificationRepository,
                    consentValidator,
                    ciUniquenessValidator,
                    userAppender,
                    consentAppender,
                    notificationPreferenceAppender,
                    ciRegistryRepository);
        }

        @Bean
        UserWithdrawalProcessor userWithdrawalProcessor(
                UserRepository userRepository,
                CiRegistryRepository ciRegistryRepository,
                IdentityVerificationRepository identityVerificationRepository,
                NotificationPreferenceRemover notificationPreferenceRemover,
                RecordingPublisher publisher) {
            return new UserWithdrawalProcessor(
                    userRepository,
                    ciRegistryRepository,
                    identityVerificationRepository,
                    notificationPreferenceRemover,
                    publisher);
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
