package com.example.auth.common.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.common.auth.jwt.InMemorySigningKeyStore;
import com.example.auth.common.auth.jwt.JwtIssuer;
import com.example.auth.common.auth.jwt.JwtVerifier;
import com.example.auth.common.auth.jwt.SigningKeyRing;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 두 키링(다중 인스턴스 시뮬레이션)이 공유 스토어 위에서 상호 토큰을 검증하는지 {@link JwtCryptoConfig}
 * 실배선(엔코더·디코더)으로 검증한다 — 미지 {@code kid} 재적재가 회전 직후 토큰을 재적재 주기 없이 즉시
 * 수용하는 계약을 포함한다.
 */
class JwtMultiInstanceVerificationTest {

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final List<String> ROLES = List.of("USER");

    private final JwtCryptoConfig config = new JwtCryptoConfig();
    private final InMemorySigningKeyStore sharedStore = new InMemorySigningKeyStore();

    @Test
    void tokenIssuedByOneInstanceVerifiesOnTheOther() {
        SigningKeyRing ringA = config.signingKeyRing(sharedStore, 30, 15);
        SigningKeyRing ringB = config.signingKeyRing(sharedStore, 30, 15);
        JwtIssuer issuerA = config.jwtIssuer(config.jwtEncoder(ringA), "test-issuer");
        JwtVerifier verifierB = config.jwtVerifier(config.jwtDecoder(ringB));

        UUID userId = UUID.randomUUID();
        String token = issuerA.issueAccess(userId, UUID.randomUUID(), ROLES, TTL, Instant.now());

        assertThat(verifierB.verify(token).userId()).isEqualTo(userId);
    }

    @Test
    void rotationOnOneInstanceIsVerifiableImmediatelyOnTheOtherViaUnknownKidReload() {
        SigningKeyRing ringA = config.signingKeyRing(sharedStore, 30, 15);
        SigningKeyRing ringB = config.signingKeyRing(sharedStore, 30, 15);
        JwtIssuer issuerA = config.jwtIssuer(config.jwtEncoder(ringA), "test-issuer");
        JwtVerifier verifierB = config.jwtVerifier(config.jwtDecoder(ringB));

        UUID userId = UUID.randomUUID();
        String tokenBeforeRotation = issuerA.issueAccess(userId, UUID.randomUUID(), ROLES, TTL, Instant.now());

        // A만 회전 — B는 재적재 주기를 기다리지 않고 미지 kid 재적재로 새 키 토큰을 즉시 검증한다.
        ringA.rotate(Instant.now());
        String tokenAfterRotation = issuerA.issueAccess(userId, UUID.randomUUID(), ROLES, TTL, Instant.now());

        assertThat(verifierB.verify(tokenAfterRotation).userId()).isEqualTo(userId);
        // 직전 키 토큰도 유예 창 안이라 계속 검증된다.
        assertThat(verifierB.verify(tokenBeforeRotation).userId()).isEqualTo(userId);
    }
}
