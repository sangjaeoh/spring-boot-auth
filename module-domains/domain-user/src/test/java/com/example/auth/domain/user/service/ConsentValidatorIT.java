package com.example.auth.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.core.crypto.EnvelopeCipher;
import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.TermsDocumentRepository;
import com.example.auth.domain.user.repository.TermsVersionRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
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
 * 가입 동의 검증 IT: Flyway 시드(필수 3종 v1)에 대조해 커버리지·버전 일치를 검증한다.
 */
@SpringBootTest
@Import({JpaConfig.class, ConsentValidatorIT.TestBeans.class})
@Testcontainers
class ConsentValidatorIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ConsentValidator validator;

    @Test
    void acceptsAllRequiredCurrentVersions() {
        assertThatCode(() -> validator.validateForSignup(
                        Map.of(TermsType.SERVICE, 1, TermsType.PRIVACY_REQUIRED, 1, TermsType.AGE14, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenRequiredTypeMissing() {
        assertThatThrownBy(
                        () -> validator.validateForSignup(Map.of(TermsType.SERVICE, 1, TermsType.PRIVACY_REQUIRED, 1)))
                .isInstanceOf(UserException.class)
                .satisfies(e -> assertThat(((UserException) e).getErrorCode())
                        .isEqualTo(UserErrorCode.REQUIRED_CONSENT_MISSING));
    }

    @Test
    void rejectsStaleOrUnknownVersion() {
        assertThatThrownBy(() -> validator.validateForSignup(
                        Map.of(TermsType.SERVICE, 99, TermsType.PRIVACY_REQUIRED, 1, TermsType.AGE14, 1)))
                .isInstanceOf(UserException.class)
                .satisfies(e ->
                        assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.TERMS_VERSION_INVALID));
    }

    @Test
    void rejectsTypeWithoutAnyEffectiveVersion() {
        // MARKETING은 시드에 없다 — 발효 버전이 없는 유형에 대한 동의는 버전 불일치로 거부된다.
        assertThatThrownBy(() -> validator.validateForSignup(Map.of(
                        TermsType.SERVICE, 1,
                        TermsType.PRIVACY_REQUIRED, 1,
                        TermsType.AGE14, 1,
                        TermsType.MARKETING, 1)))
                .isInstanceOf(UserException.class)
                .satisfies(e ->
                        assertThat(((UserException) e).getErrorCode()).isEqualTo(UserErrorCode.TERMS_VERSION_INVALID));
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
        ConsentValidator consentValidator(
                TermsDocumentRepository documentRepository, TermsVersionRepository versionRepository) {
            return new ConsentValidator(documentRepository, versionRepository);
        }
    }
}
