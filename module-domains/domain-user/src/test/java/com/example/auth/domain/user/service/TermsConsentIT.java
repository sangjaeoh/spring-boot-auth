package com.example.auth.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.crypto.EnvelopeCipher;
import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.common.messaging.IntegrationEvent;
import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.user.entity.ConsentAction;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.event.ConsentGiven;
import com.example.auth.domain.user.event.ConsentWithdrawn;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.info.ConsentStateInfo;
import com.example.auth.domain.user.info.RequiredConsentGapInfo;
import com.example.auth.domain.user.repository.ConsentRecordRepository;
import com.example.auth.domain.user.repository.ConsentStateRepository;
import com.example.auth.domain.user.repository.TermsDocumentRepository;
import com.example.auth.domain.user.repository.TermsVersionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
 * 약관 발행·재동의 게이트·이용 중 동의/철회 IT: 버전 단조 발행, 필수 새 버전 발행 시 재동의 필요 파생,
 * ConsentState fold 정합, 필수 철회 거부를 실 PostgreSQL(Flyway 시드)에 대해 검증한다.
 */
@SpringBootTest
@Import({JpaConfig.class, TermsConsentIT.TestBeans.class})
@Testcontainers
class TermsConsentIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TermsVersionAppender termsVersionAppender;

    @Autowired
    private ConsentProcessor consentProcessor;

    @Autowired
    private ConsentAppender consentAppender;

    @Autowired
    private ConsentStateReader consentStateReader;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    @Autowired
    private RecordingPublisher publisher;

    @Test
    void publishesMonotonicVersionsPerType() {
        int second = termsVersionAppender.publish(TermsType.AGE14, "만 14세 확인 v2", Instant.now());
        int third = termsVersionAppender.publish(TermsType.AGE14, "만 14세 확인 v3", Instant.now());

        assertThat(second).isEqualTo(2);
        assertThat(third).isEqualTo(3);
    }

    @Test
    void rejectsPublishForUnregisteredType() {
        assertThatThrownBy(() -> termsVersionAppender.publish(TermsType.THIRD_PARTY, "제3자 제공", Instant.now()))
                .isInstanceOf(UserException.class)
                .satisfies(e ->
                        assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.TERMS_TYPE_NOT_FOUND));
    }

    @Test
    void requiredNewVersionCreatesReconsentGapAndAgreeClearsIt() {
        UUID userId = UUID.randomUUID();
        // 가입 시점 동의(현행 v1)를 fold까지 적재한다.
        consentAppender.append(userId, TermsType.SERVICE, 1, ConsentAction.AGREE, null, Instant.now());
        assertThat(gapsOf(userId, TermsType.SERVICE)).isEmpty();

        int newVersion = termsVersionAppender.publish(TermsType.SERVICE, "서비스 약관 개정", Instant.now());

        List<RequiredConsentGapInfo> gaps = gapsOf(userId, TermsType.SERVICE);
        assertThat(gaps).hasSize(1);
        assertThat(gaps.getFirst().requiredVersion()).isEqualTo(newVersion);
        assertThat(gaps.getFirst().agreedVersion()).isEqualTo(1);

        consentProcessor.agree(userId, TermsType.SERVICE, newVersion, null);

        assertThat(gapsOf(userId, TermsType.SERVICE)).isEmpty();
        ConsentStateInfo state = stateOf(userId, TermsType.SERVICE);
        assertThat(state.currentAction()).isEqualTo(ConsentAction.AGREE);
        assertThat(state.agreedVersion()).isEqualTo(newVersion);
        assertThat(publisher.eventsOf(ConsentGiven.class)).anySatisfy(event -> {
            assertThat(event.userId()).isEqualTo(userId);
            assertThat(event.termsVersion()).isEqualTo(newVersion);
        });
    }

    @Test
    void rejectsAgreeOnStaleVersion() {
        UUID userId = UUID.randomUUID();
        termsVersionAppender.publish(TermsType.PRIVACY_REQUIRED, "개정", Instant.now());

        assertThatThrownBy(() -> consentProcessor.agree(userId, TermsType.PRIVACY_REQUIRED, 1, null))
                .isInstanceOf(UserException.class)
                .satisfies(e ->
                        assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.TERMS_VERSION_INVALID));
    }

    @Test
    void rejectsWithdrawalOfRequiredTerms() {
        UUID userId = UUID.randomUUID();
        consentAppender.append(userId, TermsType.SERVICE, 1, ConsentAction.AGREE, null, Instant.now());

        assertThatThrownBy(() -> consentProcessor.withdraw(userId, TermsType.SERVICE))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.REQUIRED_CONSENT_WITHDRAWAL));
    }

    @Test
    void withdrawsOptionalConsentAndFoldsState() {
        UUID userId = UUID.randomUUID();
        consentProcessor.agree(userId, TermsType.MARKETING, 1, NotificationChannel.EMAIL);

        consentProcessor.withdraw(userId, TermsType.MARKETING);

        ConsentStateInfo state = stateOf(userId, TermsType.MARKETING);
        assertThat(state.currentAction()).isEqualTo(ConsentAction.WITHDRAW);
        assertThat(state.agreedVersion()).isNull();
        // append-only: AGREE·WITHDRAW 두 건이 로그에 남는다.
        assertThat(consentRecordRepository.findAll().stream()
                        .filter(record -> userId.equals(record.getUserId()))
                        .count())
                .isEqualTo(2);
        assertThat(publisher.eventsOf(ConsentWithdrawn.class))
                .anySatisfy(event -> assertThat(event.userId()).isEqualTo(userId));
    }

    @Test
    void rejectsWithdrawalWithoutActiveConsent() {
        assertThatThrownBy(() -> consentProcessor.withdraw(UUID.randomUUID(), TermsType.MARKETING))
                .isInstanceOf(UserException.class)
                .satisfies(
                        e -> assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.CONSENT_NOT_FOUND));
    }

    private List<RequiredConsentGapInfo> gapsOf(UUID userId, TermsType type) {
        return consentStateReader.findRequiredGaps(userId).stream()
                .filter(gap -> gap.termsType() == type)
                .toList();
    }

    private ConsentStateInfo stateOf(UUID userId, TermsType type) {
        return consentStateReader.getStates(userId).stream()
                .filter(state -> state.termsType() == type)
                .findFirst()
                .orElseThrow();
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
        RecordingPublisher messagePublisher() {
            return new RecordingPublisher();
        }

        @Bean
        TermsVersionAppender termsVersionAppender(
                TermsDocumentRepository documentRepository, TermsVersionRepository versionRepository) {
            return new TermsVersionAppender(documentRepository, versionRepository);
        }

        @Bean
        ConsentValidator consentValidator(
                TermsDocumentRepository documentRepository, TermsVersionRepository versionRepository) {
            return new ConsentValidator(documentRepository, versionRepository);
        }

        @Bean
        ConsentAppender consentAppender(
                ConsentRecordRepository consentRecordRepository, ConsentStateRepository consentStateRepository) {
            return new ConsentAppender(consentRecordRepository, consentStateRepository);
        }

        @Bean
        ConsentProcessor consentProcessor(
                ConsentValidator consentValidator,
                ConsentAppender consentAppender,
                TermsDocumentRepository termsDocumentRepository,
                ConsentStateRepository consentStateRepository,
                RecordingPublisher publisher) {
            return new ConsentProcessor(
                    consentValidator, consentAppender, termsDocumentRepository, consentStateRepository, publisher);
        }

        @Bean
        ConsentStateReader consentStateReader(
                ConsentStateRepository consentStateRepository,
                TermsDocumentRepository termsDocumentRepository,
                TermsVersionRepository termsVersionRepository) {
            return new ConsentStateReader(consentStateRepository, termsDocumentRepository, termsVersionRepository);
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
