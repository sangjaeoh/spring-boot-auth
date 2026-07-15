package com.example.auth.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.common.core.crypto.BlindIndexer;
import com.example.auth.common.core.crypto.EnvelopeCipher;
import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Contact;
import com.example.auth.domain.user.entity.Email;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.PhoneNumber;
import com.example.auth.domain.user.entity.Profile;
import com.example.auth.domain.user.entity.User;
import com.example.auth.domain.user.service.UserAppender;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * usr 영속 E2E: 봉투 암호화 컬럼(평문 부재)·전화 blind index 동등조회·복호 왕복을 실 PostgreSQL로 검증한다.
 *
 * <p>실 crypto 강도는 infra-crypto 단위테스트가 커버한다. 이 IT는 <b>영속/조회 배선</b>(컨버터 적용·blind
 * index 컬럼 조회)을 증명한다 — 계층상 domain은 infra-crypto에 의존하지 않으므로 fake 포트를 명시 등록한다
 * (PasswordHistoryIT의 FAKE_HASHER 선례). 중첩 임베더블(Contact→PhoneNumber) + 컨버터 주입이 실제로
 * 저장·로드됨을 함께 실증한다.
 */
@SpringBootTest
@Import({JpaConfig.class, UserPersistenceIT.TestCryptoBeans.class})
@Testcontainers
class UserPersistenceIT {

    private static final String NAME = "홍길동";
    private static final LocalDate BIRTH = LocalDate.of(1990, 3, 14);
    private static final String PHONE = "+821012345678";
    private static final String EMAIL = "hong@example.com";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserAppender userAppender;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlindIndexer blindIndexer;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void encryptsPiiColumnsAndSupportsPhoneBlindIndexLookup() {
        Profile profile = Profile.of(NAME, BIRTH, Gender.MALE);
        Contact contact = Contact.of(Email.of(EMAIL), PhoneNumber.of(Carrier.SKT, PHONE));

        UUID userId = userAppender.register(profile, contact, "ci-hash-abc");

        // 1) PII 컬럼이 컨버터로 변형됐다(암호화 배선). 저장값이 사이퍼 출력과 정확히 일치함을 못박는다
        //    (실 crypto 기밀성은 infra-crypto 단위테스트가 커버 — 여기 fake는 가역 base64라 배선만 증명).
        Map<String, Object> row = jdbc.queryForMap(
                "select name, birth_date, contact_phone, contact_email, gender from usr.users where id = ?", userId);
        assertThat(row.get("name")).isEqualTo(TestCryptoBeans.FAKE_CIPHER.encrypt(NAME));
        assertThat(row.get("birth_date")).isEqualTo(TestCryptoBeans.FAKE_CIPHER.encrypt(BIRTH.toString()));
        assertThat(row.get("contact_phone")).isEqualTo(TestCryptoBeans.FAKE_CIPHER.encrypt(PHONE));
        assertThat((String) row.get("name")).doesNotContain(NAME); // 원문이 그대로 있지 않다(2차 방어)
        // 평문 컬럼(email·gender)은 변형 없이 그대로.
        assertThat(row.get("contact_email")).isEqualTo(EMAIL);
        assertThat(row.get("gender")).isEqualTo("MALE");

        // 2) 전화 blind index 동등조회.
        List<User> found = userRepository.findByContactPhoneBidx(blindIndexer.blindIndex(PHONE));
        assertThat(found).extracting(User::getId).containsExactly(userId);

        // 3) 복호 왕복(로드 시 PII 원문 복원).
        User loaded = userRepository.findById(userId).orElseThrow();
        assertThat(loaded.getProfile().name()).isEqualTo(NAME);
        assertThat(loaded.getProfile().birthDate()).isEqualTo(BIRTH);
        assertThat(loaded.getContact().contactPhone().number()).isEqualTo(PHONE);
        assertThat(loaded.getContact().contactEmail().value()).isEqualTo(EMAIL);
    }

    /**
     * fake crypto 포트 + 도메인 서비스 조립(스캔 없는 테스트 앱이라 명시 @Bean 등록).
     *
     * <p>{@code EnvelopeCipher} 빈만 등록하면 Hibernate가 {@code SpringBeanContainer}로 PII 컨버터를 fresh
     * 조립하며 이 사이퍼를 주입한다(컨버터를 @Bean으로 둬도 JPA 규약상 무시되고 fresh 생성됨). fake 사이퍼는
     * 가역 base64(평문 substring 미포함)라 왕복·평문부재를 함께 만족한다.
     */
    @TestConfiguration
    static class TestCryptoBeans {

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
        UserAppender userAppender(UserRepository repository, BlindIndexer indexer) {
            return new UserAppender(repository, indexer);
        }
    }
}
