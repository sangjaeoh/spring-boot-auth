package com.example.auth.common.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWK;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SigningKeyRingTest {

    private static final Duration GRACE = Duration.ofMinutes(30);
    private static final Duration ROTATION_PERIOD = Duration.ofDays(90);

    private final InMemorySigningKeyStore store = new InMemorySigningKeyStore();

    @Test
    void freshRingPublishesSinglePublicKey() {
        SigningKeyRing ring = new SigningKeyRing(store, GRACE);

        List<JWK> keys = ring.publicJwkSet(Instant.now()).getKeys();

        assertThat(keys).hasSize(1);
        assertThat(keys).allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
        assertThat(keys.get(0).getKeyID()).isEqualTo(ring.currentSigningKey().getKeyID());
    }

    @Test
    void rotationPromotesNewCurrentAndKeepsPreviousInGrace() {
        SigningKeyRing ring = new SigningKeyRing(store, GRACE);
        String previousKid = ring.currentSigningKey().getKeyID();
        // 회전 시각은 부트스트랩 키 생성 이후여야 한다(현재 키 = 스토어 최신 createdAt).
        Instant base = Instant.now().plus(Duration.ofMinutes(1));

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
        SigningKeyRing ring = new SigningKeyRing(store, GRACE);
        String previousKid = ring.currentSigningKey().getKeyID();
        Instant base = Instant.now().plus(Duration.ofMinutes(1));
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
        SigningKeyRing ring = new SigningKeyRing(store, GRACE);
        Instant base = Instant.now().plus(Duration.ofMinutes(1));
        ring.rotate(base);
        String kidAfterFirst = ring.currentSigningKey().getKeyID();

        // 유예 창(30분)을 지나 재회전 — 첫 유예 키는 축출되고 방금 직전 키만 유예로 남는다.
        ring.rotate(base.plus(Duration.ofMinutes(31)));

        List<JWK> keys = ring.publicJwkSet(base.plus(Duration.ofMinutes(31))).getKeys();
        assertThat(keys).hasSize(2);
        assertThat(keys)
                .extracting(JWK::getKeyID)
                .containsExactlyInAnyOrder(ring.currentSigningKey().getKeyID(), kidAfterFirst);
        // 유예가 끝난 키는 게시만이 아니라 스토어에서도 정리된다.
        assertThat(store.loadAll()).hasSize(2);
    }

    @Test
    void restartOverSameStoreKeepsCurrentKey() {
        SigningKeyRing ring = new SigningKeyRing(store, GRACE);
        String kidBeforeRestart = ring.currentSigningKey().getKeyID();

        // 재시작 시뮬레이션 — 같은 스토어 위에 새 키링(새 컨텍스트)을 올려도 새 kid를 만들지 않는다.
        SigningKeyRing restarted = new SigningKeyRing(store, GRACE);

        assertThat(restarted.currentSigningKey().getKeyID()).isEqualTo(kidBeforeRestart);
        assertThat(store.loadAll()).hasSize(1);
    }

    @Test
    void bootstrapRaceConvergesBothInstancesToOneKey() {
        // 동시 부팅 경합 — 한 인스턴스가 먼저 부트스트랩하면 다른 인스턴스는 CAS 실패 후 승자 키를 채택한다.
        SigningKeyRing winner = new SigningKeyRing(store, GRACE);
        String winnerKid = winner.currentSigningKey().getKeyID();

        SigningKeyRing loser = new SigningKeyRing(store, GRACE);

        assertThat(loser.currentSigningKey().getKeyID()).isEqualTo(winnerKid);
    }

    @Test
    void rotateIfDueRotatesOnlyPastRotationPeriod() {
        SigningKeyRing ring = new SigningKeyRing(store, GRACE);
        String initialKid = ring.currentSigningKey().getKeyID();
        Instant bootstrapAt = store.loadAll().get(0).createdAt();

        assertThat(ring.rotateIfDue(bootstrapAt.plus(Duration.ofDays(89)), ROTATION_PERIOD))
                .isFalse();
        assertThat(ring.currentSigningKey().getKeyID()).isEqualTo(initialKid);

        assertThat(ring.rotateIfDue(bootstrapAt.plus(ROTATION_PERIOD), ROTATION_PERIOD))
                .isTrue();
        assertThat(ring.currentSigningKey().getKeyID()).isNotEqualTo(initialKid);
    }

    @Test
    void refreshAdoptsRotationPerformedByAnotherInstance() {
        SigningKeyRing instanceA = new SigningKeyRing(store, GRACE);
        SigningKeyRing instanceB = new SigningKeyRing(store, GRACE);
        String kidBeforeRotation = instanceA.currentSigningKey().getKeyID();
        Instant base = Instant.now().plus(Duration.ofMinutes(1));

        instanceA.rotate(base);
        instanceB.refresh();

        String rotatedKid = instanceA.currentSigningKey().getKeyID();
        assertThat(instanceB.currentSigningKey().getKeyID()).isEqualTo(rotatedKid);
        // 직전 키는 유예 창 동안 두 인스턴스 모두에서 게시된다.
        assertThat(instanceB.publicJwkSet(base).getKeys())
                .extracting(JWK::getKeyID)
                .containsExactlyInAnyOrder(kidBeforeRotation, rotatedKid);
    }
}
