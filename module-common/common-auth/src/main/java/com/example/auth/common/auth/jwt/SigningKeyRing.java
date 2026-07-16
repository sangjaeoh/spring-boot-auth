package com.example.auth.common.auth.jwt;

import com.example.auth.common.core.id.UuidV7Generator;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Access 토큰 RS256 서명키를 회전 창과 함께 보관한다.
 *
 * <p>서명은 항상 현재 키로 하고, 검증·게시는 현재 키 + 유예 창 안의 직전 키들을 노출한다. 회전 직전 발급된
 * 토큰(수명 ≤ Access TTL)이 회전 이후에도 유예 창 동안 검증되게 하려는 것이다(유예 창 ≥ 2×Access TTL).
 *
 * <p>진실원본은 {@link SigningKeyStore}다 — 현재 키는 스토어의 최신(createdAt) 키이고, 이전 키는 후속 키
 * 생성 시각 + 유예 창 동안 게시된다. 인스턴스는 스냅샷을 메모리에 들고 {@link #refresh}로 스토어와 수렴하므로
 * 재시작해도 같은 키를 이어 쓰고, 다중 인스턴스가 같은 키·JWKS를 공유한다. 회전·부트스트랩은 스토어의 CAS
 * 추가로 직렬화된다. 서명·검증·회전·재적재가 동시 진행돼도 안전하다.
 */
public final class SigningKeyRing {

    private final SigningKeyStore store;
    private final Duration graceWindow;
    // createdAt 내림차순 불변 스냅샷(첫 원소 = 현재 키). 빈 리스트 = 미초기화(스토어 미접근).
    private volatile List<KeyRecord> keys = List.of();

    /**
     * 키링을 스토어 위에 조립한다. 스토어 적재는 첫 사용(또는 {@link #initialize}) 시점으로 늦춘다 —
     * 빈 생성이 스키마 마이그레이션보다 먼저 일어나도 기동이 깨지지 않게 한다.
     *
     * @param graceWindow 회전된 키가 검증·게시에 남아있는 유예 기간
     */
    public SigningKeyRing(SigningKeyStore store, Duration graceWindow) {
        this.store = store;
        this.graceWindow = graceWindow;
    }

    /**
     * 스토어에서 키를 적재하고, 비어 있으면 첫 키를 생성·등록한다(멱등 — 이미 초기화됐으면 무시).
     *
     * @throws IllegalStateException 초기화 후에도 스토어에 키가 없을 때
     */
    public synchronized void initialize() {
        if (!keys.isEmpty()) {
            return;
        }
        List<KeyRecord> loaded = load();
        if (loaded.isEmpty()) {
            // 동시 부팅 경합은 스토어 CAS가 직렬화한다 — 패자는 승자의 키를 재적재해 채택한다.
            store.append(null, toStored(generateKey(), Instant.now()));
            loaded = load();
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("서명키 초기화 실패 — 스토어에 키가 없다");
        }
        this.keys = loaded;
    }

    /**
     * 현재 서명키(개인키 포함)를 반환한다.
     */
    public RSAKey currentSigningKey() {
        return currentKeys().get(0).rsaKey();
    }

    /**
     * {@code now} 기준 검증·게시 대상 공개키 집합을 반환한다(현재 키 + 미만료 유예 키, 개인 파라미터 제거).
     */
    public JWKSet publicJwkSet(Instant now) {
        List<KeyRecord> snapshot = currentKeys();
        List<JWK> published = new ArrayList<>();
        published.add(snapshot.get(0).rsaKey());
        for (int i = 1; i < snapshot.size(); i++) {
            if (publishUntil(snapshot, i).isAfter(now)) {
                published.add(snapshot.get(i).rsaKey());
            }
        }
        return new JWKSet(published).toPublicJWKSet();
    }

    /**
     * {@code now} 기준 게시 대상 공개키 집합을 JWKS JSON 표현으로 반환한다(경계용 — JOSE 타입 미노출).
     */
    public Map<String, Object> publicJwks(Instant now) {
        return publicJwkSet(now).toJSONObject();
    }

    /**
     * 새 현재 키를 생성해 스토어에 CAS 추가하고 직전 키를 유예 창으로 옮긴다(유예가 끝난 키는 스토어에서
     * 정리). 다른 인스턴스가 먼저 회전했으면 생성 없이 그 결과를 채택한다.
     */
    public synchronized void rotate(Instant now) {
        List<KeyRecord> latest = load();
        @Nullable
        String expectedNewestKid = latest.isEmpty() ? null : latest.get(0).kid();
        if (store.append(expectedNewestKid, toStored(generateKey(), now))) {
            this.keys = purgeExpired(load(), now);
            return;
        }
        this.keys = load();
    }

    /**
     * 현재 키 나이가 {@code rotationPeriod}에 도달했으면 회전한다. 회전 수행 여부를 반환한다.
     */
    public synchronized boolean rotateIfDue(Instant now, Duration rotationPeriod) {
        refresh();
        if (currentKeys().get(0).createdAt().plus(rotationPeriod).isAfter(now)) {
            return false;
        }
        rotate(now);
        return true;
    }

    /**
     * 스토어에서 스냅샷을 재적재해 다른 인스턴스의 회전·부트스트랩과 수렴한다.
     */
    public synchronized void refresh() {
        List<KeyRecord> loaded = load();
        if (!loaded.isEmpty()) {
            this.keys = loaded;
        }
    }

    private List<KeyRecord> currentKeys() {
        List<KeyRecord> snapshot = keys;
        if (snapshot.isEmpty()) {
            initialize();
            snapshot = keys;
        }
        return snapshot;
    }

    /**
     * 키 {@code index}의 게시 만료 시각 — 후속(더 새로운) 키의 생성 시각 + 유예 창.
     */
    private Instant publishUntil(List<KeyRecord> snapshot, int index) {
        return snapshot.get(index - 1).createdAt().plus(graceWindow);
    }

    private List<KeyRecord> purgeExpired(List<KeyRecord> loaded, Instant now) {
        List<KeyRecord> retained = new ArrayList<>();
        List<String> expiredKids = new ArrayList<>();
        retained.add(loaded.get(0));
        for (int i = 1; i < loaded.size(); i++) {
            if (publishUntil(loaded, i).isAfter(now)) {
                retained.add(loaded.get(i));
            } else {
                expiredKids.add(loaded.get(i).kid());
            }
        }
        if (!expiredKids.isEmpty()) {
            store.purge(expiredKids);
        }
        return List.copyOf(retained);
    }

    private List<KeyRecord> load() {
        return store.loadAll().stream()
                .map(KeyRecord::from)
                .sorted(Comparator.comparing(KeyRecord::createdAt)
                        .thenComparing(KeyRecord::kid)
                        .reversed())
                .toList();
    }

    private static StoredSigningKey toStored(RSAKey key, Instant createdAt) {
        return new StoredSigningKey(key.getKeyID(), key.toJSONString(), createdAt);
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

    private record KeyRecord(String kid, RSAKey rsaKey, Instant createdAt) {
        private static KeyRecord from(StoredSigningKey stored) {
            try {
                return new KeyRecord(stored.kid(), RSAKey.parse(stored.privateJwkJson()), stored.createdAt());
            } catch (ParseException e) {
                throw new IllegalStateException("저장된 서명키 JWK 해석 실패: kid=" + stored.kid(), e);
            }
        }
    }
}
