package com.example.auth.external.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AppleClientSecretFactoryTest {

    private static final String TEAM_ID = "TEAM123456";
    private static final String KEY_ID = "KEY1234567";
    private static final String CLIENT_ID = "com.example.service";

    private static KeyPair keyPair;
    private static AppleClientSecretFactory factory;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
        factory = new AppleClientSecretFactory(TEAM_ID, KEY_ID, CLIENT_ID, toPem(keyPair));
    }

    @Test
    void generatesEs256JwtWithAppleContract() throws Exception {
        Instant now = Instant.parse("2026-07-15T00:00:00Z");

        SignedJWT jwt = SignedJWT.parse(factory.generate(now, Duration.ofDays(30)));

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())))
                .isTrue();
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM_ID);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isEqualTo(Date.from(now.plus(Duration.ofDays(30))));
    }

    @Test
    void rejectsTtlBeyondAppleLimit() {
        assertThatThrownBy(() -> factory.generate(Instant.now(), Duration.ofDays(181)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonEcPrivateKey() {
        assertThatThrownBy(() -> new AppleClientSecretFactory(TEAM_ID, KEY_ID, CLIENT_ID, "not-a-pem"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String toPem(KeyPair pair) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .encodeToString(pair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }
}
