package com.example.auth.common.auth.jwt;

import com.example.auth.common.core.id.UuidV7Generator;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Access 토큰 RS256 서명키를 회전 창과 함께 보관한다.
 *
 * <p>서명은 항상 현재 키로 하고, 검증·게시는 현재 키 + 유예 창 안의 직전 키들을 노출한다. 회전 직전 발급된
 * 토큰(수명 ≤ Access TTL)이 회전 이후에도 유예 창 동안 검증되게 하려는 것이다(유예 창 ≥ 2×Access TTL).
 *
 * <p>키는 인스턴스 로컬 메모리에 있다. 내구·다중 인스턴스 공유·시크릿매니저 주입은 이 클래스 밖의 후속
 * 워크스트림이 소유한다. 서명·검증·회전이 동시 진행돼도 안전하다.
 */
public final class SigningKeyRing {

    private final Duration graceWindow;
    private volatile State state;

    /**
     * 새 현재 키 하나로 키링을 초기화한다.
     *
     * @param graceWindow 회전된 키가 검증·게시에 남아있는 유예 기간
     */
    public SigningKeyRing(Duration graceWindow) {
        this.graceWindow = graceWindow;
        this.state = new State(generateKey(), List.of());
    }

    /**
     * 현재 서명키(개인키 포함)를 반환한다.
     */
    public RSAKey currentSigningKey() {
        return state.current();
    }

    /**
     * {@code now} 기준 검증·게시 대상 공개키 집합을 반환한다(현재 키 + 미만료 유예 키, 개인 파라미터 제거).
     */
    public JWKSet publicJwkSet(Instant now) {
        State snapshot = state;
        List<JWK> keys = new ArrayList<>();
        keys.add(snapshot.current());
        for (GraceKey grace : snapshot.graces()) {
            if (grace.expiresAt().isAfter(now)) {
                keys.add(grace.publicKey());
            }
        }
        return new JWKSet(keys).toPublicJWKSet();
    }

    /**
     * {@code now} 기준 게시 대상 공개키 집합을 JWKS JSON 표현으로 반환한다(경계용 — JOSE 타입 미노출).
     */
    public Map<String, Object> publicJwks(Instant now) {
        return publicJwkSet(now).toJSONObject();
    }

    /**
     * 새 현재 키를 생성하고 직전 키를 유예 창으로 옮긴다(만료 유예 키는 축출).
     */
    public synchronized void rotate(Instant now) {
        State snapshot = state;
        List<GraceKey> graces = new ArrayList<>();
        graces.add(new GraceKey(snapshot.current().toPublicJWK(), now.plus(graceWindow)));
        for (GraceKey grace : snapshot.graces()) {
            if (grace.expiresAt().isAfter(now)) {
                graces.add(grace);
            }
        }
        this.state = new State(generateKey(), graces);
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(UuidV7Generator.generate().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("RSA 서명키 생성 실패", e);
        }
    }

    private record State(RSAKey current, List<GraceKey> graces) {
        private State {
            graces = List.copyOf(graces);
        }
    }

    private record GraceKey(RSAKey publicKey, Instant expiresAt) {}
}
