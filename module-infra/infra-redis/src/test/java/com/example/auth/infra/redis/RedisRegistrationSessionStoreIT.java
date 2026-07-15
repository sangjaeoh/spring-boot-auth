package com.example.auth.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.entity.RegistrationStep;
import com.example.auth.domain.auth.entity.RegistrationType;
import com.example.auth.domain.auth.port.RegistrationSnapshot;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 온보딩 세션 스토어 IT: 생성/조회 왕복·스텝 마킹·TTL 자동 파기·만료 후 마킹의 좀비 부활 차단을
 * 실 Redis(Testcontainers)로 검증한다.
 */
@SpringBootTest
@Testcontainers
class RedisRegistrationSessionStoreIT {

    private static final Duration TTL = Duration.ofMinutes(30);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisRegistrationSessionStore store;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void createFindRoundTripAndTtlIsSet() {
        UUID id = UUID.randomUUID();
        store.create(id, RegistrationType.LOCAL, "token-hash", "a@example.com", "chal-email-1", TTL);

        RegistrationSnapshot snapshot = store.find(id).orElseThrow();
        assertThat(snapshot.type()).isEqualTo(RegistrationType.LOCAL);
        assertThat(snapshot.tokenHash()).isEqualTo("token-hash");
        assertThat(snapshot.loginEmail()).isEqualTo("a@example.com");
        assertThat(snapshot.emailChallengeId()).isEqualTo("chal-email-1");
        assertThat(snapshot.phoneChallengeId()).isNull();
        assertThat(snapshot.completedSteps()).isEmpty();
        assertThat(snapshot.consents()).isEmpty();

        // TTL이 실제로 걸렸다(만료 시 자동 파기의 전제).
        Long expire = redisTemplate.getExpire("reg:" + id, TimeUnit.SECONDS);
        assertThat(expire).isPositive().isLessThanOrEqualTo(TTL.toSeconds());
    }

    @Test
    void marksStepsAndBuffersReferences() {
        UUID id = UUID.randomUUID();
        UUID verificationRef = UUID.randomUUID();
        store.create(id, RegistrationType.LOCAL, "token-hash", "b@example.com", "chal-1", TTL);

        assertThat(store.markEmailVerified(id)).isTrue();
        assertThat(store.attachPhoneChallenge(id, "chal-sms-1")).isTrue();
        assertThat(store.markPhoneVerified(id)).isTrue();
        assertThat(store.markIdentityVerified(id, verificationRef, "1:cihash")).isTrue();
        assertThat(store.markRequiredConsented(
                        id, List.of(new ConsentSelection("SERVICE", 1), new ConsentSelection("AGE14", 2))))
                .isTrue();

        RegistrationSnapshot snapshot = store.find(id).orElseThrow();
        assertThat(snapshot.completedSteps())
                .containsExactlyInAnyOrder(
                        RegistrationStep.EMAIL_VERIFIED,
                        RegistrationStep.PHONE_VERIFIED,
                        RegistrationStep.IDENTITY_VERIFIED,
                        RegistrationStep.REQUIRED_CONSENTED);
        assertThat(snapshot.phoneChallengeId()).isEqualTo("chal-sms-1");
        assertThat(snapshot.verificationRef()).isEqualTo(verificationRef);
        assertThat(snapshot.ciHash()).isEqualTo("1:cihash");
        assertThat(snapshot.consents())
                .containsExactly(new ConsentSelection("SERVICE", 1), new ConsentSelection("AGE14", 2));
    }

    @Test
    void expiredSessionIsGoneAndMarkingDoesNotResurrectIt() {
        UUID id = UUID.randomUUID();
        store.create(id, RegistrationType.LOCAL, "token-hash", "c@example.com", "chal-1", Duration.ofSeconds(1));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(store.find(id)).isEmpty());

        // 만료(자동 파기) 후 마킹은 실패해야 하고, TTL 없는 좀비 키를 만들지 않아야 한다.
        assertThat(store.markEmailVerified(id)).isFalse();
        assertThat(store.find(id)).isEmpty();
        assertThat(redisTemplate.hasKey("reg:" + id)).isFalse();
    }

    @Test
    void markingUnknownSessionReturnsFalse() {
        assertThat(store.markPhoneVerified(UUID.randomUUID())).isFalse();
        assertThat(store.attachEmailChallenge(UUID.randomUUID(), "chal")).isFalse();
    }
}
