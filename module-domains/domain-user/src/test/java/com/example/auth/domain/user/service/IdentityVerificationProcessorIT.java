package com.example.auth.domain.user.service;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.crypto.BlindIndexer;
import com.example.auth.common.core.crypto.EnvelopeCipher;
import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.CiRegistry;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.IdentityVerification;
import com.example.auth.domain.user.entity.Provider;
import com.example.auth.domain.user.entity.VerificationResult;
import com.example.auth.domain.user.entity.VerificationStatus;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.info.IdentityVerifiedInfo;
import com.example.auth.domain.user.port.IdentityProviderOutcome;
import com.example.auth.domain.user.port.IdentityProviderRequest;
import com.example.auth.domain.user.port.IdentityVerificationProvider;
import com.example.auth.domain.user.repository.CiRegistryRepository;
import com.example.auth.domain.user.repository.IdentityVerificationRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 본인인증 플로우 IT: 기관 성공 → VERIFIED 저장(PII 암호화 컬럼·ciHash 파생), 기관 실패 → FAILED,
 * CI 중복(soft-check) 거부를 실 PostgreSQL로 검증한다.
 *
 * <p>domain은 external-identity에 의존하지 않으므로(계층) 기관 포트는 테스트 fake로 조립하되, 실제 Mock
 * 어댑터와 같은 결정성(동일인 = 동일 CI)을 유지한다. 실 Mock 어댑터 배선은 app-api E2E가 커버한다.
 */
@SpringBootTest
@Import({JpaConfig.class, IdentityVerificationProcessorIT.TestBeans.class})
@Testcontainers
class IdentityVerificationProcessorIT {

    // 테스트마다 고유 정체성(전화)을 쓴다 — 컨텍스트·데이터가 공유되므로 동일 정체성(=동일 ciHash)은
    // 실행 순서에 따라 CI 중복 판정이 교차 오염된다(JUnit은 메서드 순서를 보증하지 않는다).
    private static IdentityProviderRequest request(String phone) {
        return new IdentityProviderRequest("홍길동", LocalDate.of(1990, 3, 14), Gender.MALE, Carrier.SKT, phone);
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private IdentityVerificationProcessor processor;

    @Autowired
    @Qualifier("failingProcessor")
    private IdentityVerificationProcessor failingProcessor;

    @Autowired
    private IdentityVerificationRepository verificationRepository;

    @Autowired
    private CiRegistryRepository ciRegistryRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void verifiesAndStoresEncryptedResultWithDerivedCiHash() {
        IdentityProviderRequest request = request("+821011110001");
        IdentityVerifiedInfo info = processor.verify(request);

        assertThat(info.ciHash()).startsWith("1:");

        IdentityVerification stored =
                verificationRepository.findById(info.verificationId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(stored.getUserId()).isNull();

        // 복호 왕복 — 로드된 결과 VO가 원문을 복원한다.
        VerificationResult result = requireNonNull(stored.getResult());
        assertThat(result.name()).isEqualTo("홍길동");
        assertThat(result.birthDate()).isEqualTo(LocalDate.of(1990, 3, 14));
        assertThat(result.phone()).isEqualTo("+821011110001");

        // PII 컬럼은 컨버터 출력(암호화 배선)과 정확히 일치하고 원문이 그대로 있지 않다.
        Map<String, Object> row = jdbc.queryForMap(
                "select name, birth_date, phone, di, ci_hash from usr.identity_verification where id = ?",
                info.verificationId());
        assertThat(row.get("name")).isEqualTo(TestBeans.FAKE_CIPHER.encrypt("홍길동"));
        assertThat(row.get("birth_date")).isEqualTo(TestBeans.FAKE_CIPHER.encrypt("1990-03-14"));
        assertThat(row.get("phone")).isEqualTo(TestBeans.FAKE_CIPHER.encrypt("+821011110001"));
        assertThat((String) row.get("name")).doesNotContain("홍길동");
        assertThat((String) row.get("di")).isNotEmpty();
        assertThat(row.get("ci_hash")).isEqualTo(info.ciHash());

        // 동일인 재시도는 동일 ciHash(결정적 CI 파생) — CI 원장 유일성 시맨틱의 전제.
        assertThat(processor.verify(request).ciHash()).isEqualTo(info.ciHash());
    }

    @Test
    void rejectsWhenCiAlreadyLinkedToActiveUser() {
        IdentityProviderRequest request = request("+821011110002");
        IdentityVerifiedInfo first = processor.verify(request);
        ciRegistryRepository.save(CiRegistry.link(first.ciHash(), UUID.randomUUID(), Instant.now()));

        assertThatThrownBy(() -> processor.verify(request))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.DUPLICATE_CI));

        // soft-check 거부여도 수행된 실명확인(VERIFIED)은 감사 사실로 남는다.
        long verifiedCount = verificationRepository.findAll().stream()
                .filter(v -> v.getStatus() == VerificationStatus.VERIFIED)
                .filter(v -> first.ciHash()
                        .equals(v.getResult() == null ? null : v.getResult().ciHash()))
                .count();
        assertThat(verifiedCount).isEqualTo(2);
    }

    @Test
    void failedOutcomeTransitionsToFailedAndThrows() {
        assertThatThrownBy(() -> failingProcessor.verify(request("+821011110003")))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.IDENTITY_VERIFICATION_FAILED));

        // FAILED 행은 결과 없이 남는다(embedded 전 컬럼 null → null 로드).
        IdentityVerification failed = verificationRepository.findAll().stream()
                .filter(v -> v.getStatus() == VerificationStatus.FAILED)
                .findFirst()
                .orElseThrow();
        assertThat(failed.getResult()).isNull();
    }

    /**
     * fake crypto + 결정적 fake 기관 + 도메인 서비스 조립(스캔 없는 테스트 앱이라 명시 빈 등록 —
     * {@code @Transactional} 프록시는 빈에만 적용되므로 서비스는 반드시 빈으로 만든다).
     */
    @TestConfiguration
    static class TestBeans {

        static final EnvelopeCipher FAKE_CIPHER = new EnvelopeCipher() {
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

        static final BlindIndexer FAKE_INDEXER = value -> "1:" + Integer.toHexString(value.hashCode());

        @Bean
        EnvelopeCipher envelopeCipher() {
            return FAKE_CIPHER;
        }

        @Bean
        BlindIndexer blindIndexer() {
            return FAKE_INDEXER;
        }

        @Bean
        IdentityVerificationAppender identityVerificationAppender(IdentityVerificationRepository repository) {
            return new IdentityVerificationAppender(repository, 10);
        }

        @Bean
        IdentityVerificationModifier identityVerificationModifier(IdentityVerificationRepository repository) {
            return new IdentityVerificationModifier(repository);
        }

        @Bean
        CiUniquenessValidator ciUniquenessValidator(CiRegistryRepository repository) {
            return new CiUniquenessValidator(repository, 30);
        }

        @Bean
        @Primary
        IdentityVerificationProcessor identityVerificationProcessor(
                IdentityVerificationAppender appender,
                IdentityVerificationModifier modifier,
                CiUniquenessValidator ciUniquenessValidator,
                BlindIndexer blindIndexer) {
            IdentityVerificationProvider succeeding = new IdentityVerificationProvider() {
                @Override
                public Provider provider() {
                    return Provider.MOCK;
                }

                @Override
                public IdentityProviderOutcome verify(IdentityProviderRequest request) {
                    String identity = request.name() + "|" + request.birthDate() + "|" + request.phone();
                    return new IdentityProviderOutcome.Verified(
                            request.name(),
                            request.birthDate(),
                            request.gender(),
                            request.carrier(),
                            request.phone(),
                            "ci|" + identity,
                            "di|" + identity);
                }
            };
            return new IdentityVerificationProcessor(
                    succeeding, appender, modifier, ciUniquenessValidator, blindIndexer);
        }

        @Bean
        IdentityVerificationProcessor failingProcessor(
                IdentityVerificationAppender appender,
                IdentityVerificationModifier modifier,
                CiUniquenessValidator ciUniquenessValidator,
                BlindIndexer blindIndexer) {
            IdentityVerificationProvider failing = new IdentityVerificationProvider() {
                @Override
                public Provider provider() {
                    return Provider.MOCK;
                }

                @Override
                public IdentityProviderOutcome verify(IdentityProviderRequest request) {
                    return new IdentityProviderOutcome.Failed("mock-fail");
                }
            };
            return new IdentityVerificationProcessor(failing, appender, modifier, ciUniquenessValidator, blindIndexer);
        }
    }
}
