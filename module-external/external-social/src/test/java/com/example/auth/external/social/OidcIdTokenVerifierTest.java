package com.example.auth.external.social;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.domain.auth.port.SocialIdentityOutcome;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 로컬 생성 키로 서명한 토큰을 로컬 JWKS로 검증한다 — 실 어댑터의 검증 경로(서명·iss·aud·exp·클레임
 * 추출)를 네트워크 없이 증명한다. 원격 JWKS 조회만 실 4사 조달 후 확인 대상으로 남는다.
 */
class OidcIdTokenVerifierTest {

    private static final String ISSUER = "https://idp.example.com";
    private static final String CLIENT_ID = "test-client";

    private static RSAKey signingKey;
    private static OidcIdTokenVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        verifier =
                new OidcIdTokenVerifier(ISSUER, CLIENT_ID, new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK())));
    }

    @Test
    void verifiesValidTokenAndExtractsIdentity() throws Exception {
        String token = sign(claims -> claims.claim("email", "user@example.com"));

        assertThat(verifier.verify(token))
                .isEqualTo(new SocialIdentityOutcome.Verified("subject-1", "user@example.com", false));
    }

    @Test
    void flagsApplePrivateEmailClaim() throws Exception {
        String token = sign(
                claims -> claims.claim("email", "abc@privaterelay.appleid.com").claim("is_private_email", "true"));

        assertThat(verifier.verify(token))
                .isEqualTo(new SocialIdentityOutcome.Verified("subject-1", "abc@privaterelay.appleid.com", true));
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        String token = sign(claims -> claims.audience("other-client"));

        assertThat(verifier.verify(token)).isInstanceOf(SocialIdentityOutcome.Failed.class);
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        String token = sign(claims -> claims.issuer("https://attacker.example.com"));

        assertThat(verifier.verify(token)).isInstanceOf(SocialIdentityOutcome.Failed.class);
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String token = sign(
                claims -> claims.issueTime(Date.from(past.minusSeconds(60))).expirationTime(Date.from(past)));

        assertThat(verifier.verify(token)).isInstanceOf(SocialIdentityOutcome.Failed.class);
    }

    @Test
    void rejectsTokenSignedByUnknownKey() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("other-key").generate();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("other-key").build(),
                baseClaims().build());
        jwt.sign(new RSASSASigner(otherKey));

        assertThat(verifier.verify(jwt.serialize())).isInstanceOf(SocialIdentityOutcome.Failed.class);
    }

    @Test
    void rejectsGarbageToken() {
        assertThat(verifier.verify("garbage")).isInstanceOf(SocialIdentityOutcome.Failed.class);
    }

    private static JWTClaimsSet.Builder baseClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject("subject-1")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private static String sign(Consumer<JWTClaimsSet.Builder> customizer) throws Exception {
        JWTClaimsSet.Builder claims = baseClaims();
        customizer.accept(claims);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(signingKey.getKeyID())
                        .build(),
                claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
}
