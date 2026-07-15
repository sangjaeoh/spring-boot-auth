package com.example.auth.common.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWK;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SigningKeyRingTest {

    private static final Duration GRACE = Duration.ofMinutes(30);

    @Test
    void freshRingPublishesSinglePublicKey() {
        SigningKeyRing ring = new SigningKeyRing(GRACE);

        List<JWK> keys = ring.publicJwkSet(Instant.now()).getKeys();

        assertThat(keys).hasSize(1);
        assertThat(keys).allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
        assertThat(keys.get(0).getKeyID()).isEqualTo(ring.currentSigningKey().getKeyID());
    }

    @Test
    void rotationPromotesNewCurrentAndKeepsPreviousInGrace() {
        SigningKeyRing ring = new SigningKeyRing(GRACE);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        String previousKid = ring.currentSigningKey().getKeyID();

        ring.rotate(base);

        String currentKid = ring.currentSigningKey().getKeyID();
        assertThat(currentKid).isNotEqualTo(previousKid);
        List<JWK> keys = ring.publicJwkSet(base).getKeys();
        assertThat(keys).hasSize(2);
        assertThat(keys).extracting(JWK::getKeyID).containsExactlyInAnyOrder(previousKid, currentKid);
        assertThat(keys).allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
    }

    @Test
    void graceKeyDropsFromPublicSetOnceWindowPasses() {
        SigningKeyRing ring = new SigningKeyRing(GRACE);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        String previousKid = ring.currentSigningKey().getKeyID();
        ring.rotate(base);

        assertThat(ring.publicJwkSet(base.plus(Duration.ofMinutes(29))).getKeys())
                .extracting(JWK::getKeyID)
                .contains(previousKid);
        assertThat(ring.publicJwkSet(base.plus(Duration.ofMinutes(31))).getKeys())
                .extracting(JWK::getKeyID)
                .doesNotContain(previousKid)
                .hasSize(1);
    }

    @Test
    void repeatedRotationPrunesExpiredGraceKeys() {
        SigningKeyRing ring = new SigningKeyRing(GRACE);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        ring.rotate(base);
        String kidAfterFirst = ring.currentSigningKey().getKeyID();

        // 유예 창(30분)을 지나 재회전 — 첫 유예 키는 축출되고 방금 직전 키만 유예로 남는다.
        ring.rotate(base.plus(Duration.ofMinutes(31)));

        List<JWK> keys = ring.publicJwkSet(base.plus(Duration.ofMinutes(31))).getKeys();
        assertThat(keys).hasSize(2);
        assertThat(keys)
                .extracting(JWK::getKeyID)
                .containsExactlyInAnyOrder(ring.currentSigningKey().getKeyID(), kidAfterFirst);
    }
}
