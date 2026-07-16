package com.example.auth.common.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.auth.exception.InvalidTokenException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 회전 키링 위에서 발급·검증이 유예 창·만료를 넘어 정합하는지 검증한다.
 *
 * <p>검증 키셋은 논리 시계({@code clock})로 유예 만료를 제어하고, 토큰 {@code exp}는 실시간 기준 장수명으로
 * 두어 "키 축출"과 "토큰 만료"를 분리한다(유예 밖 실패의 원인이 축출임을 확정).
 */
class JwtRotationVerificationTest {

    private static final Duration GRACE = Duration.ofMinutes(30);
    private static final Duration LONG_TTL = Duration.ofHours(1);
    private static final List<String> ROLES = List.of("USER");

    @Test
    void tokenSignedBeforeRotationStillVerifiesWithinGraceButNotAfter() throws ParseException {
        SigningKeyRing ring = new SigningKeyRing(new InMemorySigningKeyStore(), GRACE);
        // 검증 키셋의 논리 시계를 가변 캡처한다(1원소 배열 — 유예 만료 시점을 결정적으로 제어).
        // 회전·게시 판정은 createdAt 기반이라 논리 시계도 부트스트랩(now) 이후에서 시작한다.
        Instant[] clock = {Instant.now().plus(Duration.ofMinutes(1))};

        JwtEncoder encoder =
                new NimbusJwtEncoder((selector, context) -> selector.select(new JWKSet(ring.currentSigningKey())));
        JWKSource<SecurityContext> verificationSource =
                (selector, context) -> selector.select(ring.publicJwkSet(clock[0]));
        JwtDecoder decoder = NimbusJwtDecoder.withJwkSource(verificationSource)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtIssuer issuer = new JwtIssuer(encoder, "test-issuer");
        JwtVerifier verifier = new JwtVerifier(decoder);

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String kidBeforeRotation = ring.currentSigningKey().getKeyID();
        String tokenFromOldKey = issuer.issueAccess(userId, sessionId, ROLES, LONG_TTL, Instant.now());

        // Nimbus가 현재 키의 kid를 헤더에 스탬프한다(검증이 kid로 키를 해석하는 전제).
        assertThat(SignedJWT.parse(tokenFromOldKey).getHeader().getKeyID()).isEqualTo(kidBeforeRotation);
        assertThat(verifier.verify(tokenFromOldKey).userId()).isEqualTo(userId);

        Instant base = clock[0];
        ring.rotate(base);
        String kidAfterRotation = ring.currentSigningKey().getKeyID();
        assertThat(kidAfterRotation).isNotEqualTo(kidBeforeRotation);

        // 회전 직후, 유예 창 안: 옛 키 서명 토큰이 여전히 검증되고 새 키 토큰도 검증된다.
        clock[0] = base.plus(Duration.ofMinutes(10));
        assertThat(verifier.verify(tokenFromOldKey).sessionId()).isEqualTo(sessionId);
        String tokenFromNewKey = issuer.issueAccess(userId, sessionId, ROLES, LONG_TTL, Instant.now());
        assertThat(SignedJWT.parse(tokenFromNewKey).getHeader().getKeyID()).isEqualTo(kidAfterRotation);
        assertThat(verifier.verify(tokenFromNewKey).userId()).isEqualTo(userId);

        // 유예 창 밖: 옛 키가 축출돼 그 서명 토큰은 거부(exp는 아직 유효 — 축출이 원인).
        clock[0] = base.plus(Duration.ofMinutes(31));
        assertThatThrownBy(() -> verifier.verify(tokenFromOldKey)).isInstanceOf(InvalidTokenException.class);
        assertThat(verifier.verify(tokenFromNewKey).userId()).isEqualTo(userId);
    }

    @Test
    void expiredTokenIsRejected() {
        // 디코더 배선(withJwkSource)이 표준 exp 검증을 유지함을 핀한다(단수명 Access 제품 명제의 전제).
        SigningKeyRing ring = new SigningKeyRing(new InMemorySigningKeyStore(), GRACE);
        JwtEncoder encoder =
                new NimbusJwtEncoder((selector, context) -> selector.select(new JWKSet(ring.currentSigningKey())));
        JWKSource<SecurityContext> verificationSource =
                (selector, context) -> selector.select(ring.publicJwkSet(Instant.now()));
        JwtVerifier verifier = new JwtVerifier(NimbusJwtDecoder.withJwkSource(verificationSource)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build());
        JwtIssuer issuer = new JwtIssuer(encoder, "test-issuer");

        // 과거에 발급돼 이미 만료된 토큰(구조는 유효: exp > iat, 둘 다 과거).
        Instant pastIssue = Instant.now().minus(Duration.ofMinutes(10));
        String expired =
                issuer.issueAccess(UUID.randomUUID(), UUID.randomUUID(), ROLES, Duration.ofMinutes(1), pastIssue);

        assertThatThrownBy(() -> verifier.verify(expired)).isInstanceOf(InvalidTokenException.class);
    }
}
