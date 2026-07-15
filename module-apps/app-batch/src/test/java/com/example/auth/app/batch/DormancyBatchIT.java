package com.example.auth.app.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.auth.app.batch.job.DormancyNoticeJob;
import com.example.auth.app.batch.job.DormancyTransitionJob;
import com.example.auth.app.batch.job.RetentionPurgeJob;
import com.example.auth.domain.auth.entity.AuthAccount;
import com.example.auth.domain.auth.repository.AuthAccountRepository;
import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Contact;
import com.example.auth.domain.user.entity.Email;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.PhoneNumber;
import com.example.auth.domain.user.entity.Profile;
import com.example.auth.domain.user.repository.UserRepository;
import com.example.auth.domain.user.service.UserAppender;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 배치 앱 IT: 휴면 전환 잡이 회원을 DORMANT로 전환하고, 같은 프로세스의 이벤트 소비가 인증 스냅샷을
 * 단조 버전으로 동기화함을 검증한다(전 조립 컨텍스트 부팅 = 배선 스모크 겸용).
 */
@SpringBootTest
@Testcontainers
class DormancyBatchIT {

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
    private UserAppender userAppender;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthAccountRepository authAccountRepository;

    @Autowired
    private DormancyNoticeJob dormancyNoticeJob;

    @Autowired
    private DormancyTransitionJob dormancyTransitionJob;

    @Autowired
    private RetentionPurgeJob retentionPurgeJob;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void transitionJobMakesUserDormantAndSyncsAuthSnapshotInProcess() {
        Instant now = Instant.now();
        UUID userId = seedUser("dormant-batch@example.com");
        authAccountRepository.save(AuthAccount.create(userId, "dormant-batch@example.com"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var user = userRepository.findById(userId).orElseThrow();
            user.recordLogin(
                    ZonedDateTime.ofInstant(now, ZoneOffset.UTC).minusMonths(13).toInstant());
            user.markDormancyNotified(now.minusSeconds(31L * 86_400));
        });

        assertThat(dormancyTransitionJob.run(now)).containsExactly(userId);

        assertThat(userRepository.findById(userId).orElseThrow().getStatus()).isEqualTo(LifecycleStatus.DORMANT);
        AuthAccount account = authAccountRepository.findById(userId).orElseThrow();
        assertThat(account.getUserStatus()).isEqualTo(com.example.auth.domain.auth.entity.LifecycleStatus.DORMANT);
        assertThat(account.getUserStatusVersion()).isEqualTo(1);
    }

    @Test
    void noticeAndRetentionJobsRunCleanly() {
        Instant now = Instant.now();

        assertThatCode(() -> {
                    dormancyNoticeJob.run(now);
                    retentionPurgeJob.run(now);
                })
                .doesNotThrowAnyException();
    }

    private UUID seedUser(String email) {
        Profile profile = Profile.of("홍길동", java.time.LocalDate.of(1990, 1, 1), Gender.MALE);
        Contact contact = Contact.of(Email.of(email), PhoneNumber.of(Carrier.SKT, "+821012340001"));
        return userAppender.register(profile, contact, "ci-" + email);
    }
}
