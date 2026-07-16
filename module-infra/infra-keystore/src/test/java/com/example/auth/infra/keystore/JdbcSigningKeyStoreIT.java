package com.example.auth.infra.keystore;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.common.auth.config.JwtCryptoConfig;
import com.example.auth.common.auth.jwt.SigningKeyRing;
import com.example.auth.common.auth.jwt.StoredSigningKey;
import com.example.auth.common.core.crypto.EnvelopeCipher;
import com.nimbusds.jose.jwk.JWK;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 키스토어 IT: CAS 추가·봉투암호 저장·적재 왕복·정리를 실 PostgreSQL로 검증하고, 키링 결합으로 재시작·
 * 다중 인스턴스 연속성(같은 키 채택·상호 게시)을 확인한다.
 */
@SpringBootTest
@Testcontainers
class JdbcSigningKeyStoreIT {

    private static final Duration GRACE = Duration.ofMinutes(30);

    // 결정적·가역 fake 봉투암호(실 AES-GCM 경로는 infra-crypto가 커버). "env:" 접두가 암호문 마커다.
    private static final EnvelopeCipher FAKE_CIPHER = new EnvelopeCipher() {
        @Override
        public String encrypt(String plaintext) {
            return "env:" + Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String decrypt(String ciphertext) {
            return new String(
                    Base64.getDecoder().decode(ciphertext.substring("env:".length())), StandardCharsets.UTF_8);
        }
    };

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private JdbcSigningKeyStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcSigningKeyStore(jdbc, transactionManager, FAKE_CIPHER);
        jdbc.update("delete from keyring.signing_key");
    }

    @Test
    void appendsLoadsAndPurgesRoundTrip() {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        StoredSigningKey key = new StoredSigningKey("kid-1", "{\"kty\":\"RSA\",\"d\":\"secret\"}", createdAt);

        assertThat(store.append(null, key)).isTrue();

        List<StoredSigningKey> loaded = store.loadAll();
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).kid()).isEqualTo("kid-1");
        assertThat(loaded.get(0).privateJwkJson()).isEqualTo(key.privateJwkJson());
        assertThat(loaded.get(0).createdAt()).isEqualTo(createdAt);

        store.purge(List.of("kid-1"));
        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void appendIsCompareAndSetOnNewestKid() {
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        assertThat(store.append(null, new StoredSigningKey("kid-1", "{}", base)))
                .isTrue();

        // 빈 스토어 기대(null)·낡은 kid 기대는 거부되고, 실제 최신 kid 기대만 성공한다.
        assertThat(store.append(null, new StoredSigningKey("kid-2", "{}", base.plusSeconds(1))))
                .isFalse();
        assertThat(store.append("kid-0", new StoredSigningKey("kid-2", "{}", base.plusSeconds(1))))
                .isFalse();
        assertThat(store.append("kid-1", new StoredSigningKey("kid-2", "{}", base.plusSeconds(1))))
                .isTrue();
        assertThat(store.loadAll()).hasSize(2);
    }

    @Test
    void persistsPrivateKeyMaterialOnlyAsCiphertext() {
        String privateJwk = "{\"kty\":\"RSA\",\"d\":\"top-secret-private-exponent\"}";
        store.append(null, new StoredSigningKey("kid-enc", privateJwk, Instant.now()));

        String rawColumn = jdbc.queryForObject(
                "select jwk_encrypted from keyring.signing_key where kid = ?", String.class, "kid-enc");

        assertThat(rawColumn).startsWith("env:").doesNotContain("top-secret-private-exponent");
    }

    @Test
    void ringRestartOverStoreKeepsKeyAndVerifiesPriorTokens() {
        JwtCryptoConfig config = new JwtCryptoConfig();
        SigningKeyRing ring = new SigningKeyRing(store, GRACE);
        String kidBeforeRestart = ring.currentSigningKey().getKeyID();
        UUID userId = UUID.randomUUID();
        String tokenBeforeRestart = config.jwtIssuer(config.jwtEncoder(ring), "test-issuer")
                .issueAccess(userId, UUID.randomUUID(), List.of("USER"), Duration.ofMinutes(15), Instant.now());

        // 재시작 시뮬레이션 — 새 스토어 인스턴스 + 새 키링(새 컨텍스트)이 같은 DB 위에서 같은 키를 잇는다.
        JdbcSigningKeyStore restartedStore = new JdbcSigningKeyStore(jdbc, transactionManager, FAKE_CIPHER);
        SigningKeyRing restartedRing = new SigningKeyRing(restartedStore, GRACE);

        assertThat(restartedRing.currentSigningKey().getKeyID()).isEqualTo(kidBeforeRestart);
        // 재시작 직전 발급 Access가 재시작 후 검증된다(개인키 DB 왕복 온전 — 재시작마다 새 kid 401이 없다).
        assertThat(config.jwtVerifier(config.jwtDecoder(restartedRing))
                        .verify(tokenBeforeRestart)
                        .userId())
                .isEqualTo(userId);
    }

    @Test
    void rotationOnOneInstancePublishesGraceKeyOnTheOther() {
        SigningKeyRing instanceA = new SigningKeyRing(store, GRACE);
        SigningKeyRing instanceB =
                new SigningKeyRing(new JdbcSigningKeyStore(jdbc, transactionManager, FAKE_CIPHER), GRACE);
        String kidBeforeRotation = instanceA.currentSigningKey().getKeyID();
        Instant rotatedAt = Instant.now().plusSeconds(60);

        instanceA.rotate(rotatedAt);
        instanceB.refresh();

        assertThat(instanceB.currentSigningKey().getKeyID())
                .isEqualTo(instanceA.currentSigningKey().getKeyID())
                .isNotEqualTo(kidBeforeRotation);
        assertThat(instanceB.publicJwkSet(rotatedAt).getKeys())
                .extracting(JWK::getKeyID)
                .contains(kidBeforeRotation);
    }

    @Test
    void concurrentBootstrapAcrossInstancesCreatesExactlyOneKey() throws InterruptedException {
        int instances = 8;
        ExecutorService executor = Executors.newFixedThreadPool(instances);
        CountDownLatch ready = new CountDownLatch(instances);
        CountDownLatch start = new CountDownLatch(1);
        List<String> kids = new CopyOnWriteArrayList<>();
        for (int i = 0; i < instances; i++) {
            executor.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    SigningKeyRing ring =
                            new SigningKeyRing(new JdbcSigningKeyStore(jdbc, transactionManager, FAKE_CIPHER), GRACE);
                    kids.add(ring.currentSigningKey().getKeyID());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 동시 부팅 경합에서도 정확히 한 키만 생성되고 전 인스턴스가 그 키로 수렴한다.
        assertThat(store.loadAll()).hasSize(1);
        assertThat(kids).hasSize(instances);
        assertThat(kids.stream().distinct()).hasSize(1);
        UUID.fromString(kids.get(0));
    }
}
