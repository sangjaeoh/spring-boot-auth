package com.example.auth.common.auth.jwt;

import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 서명키의 내구 공유 스토어 포트다.
 *
 * <p>재시작·다중 인스턴스에서 키·JWKS가 이어지는 진실원본이다. 도메인 의미 없는 기술 포트라 common이
 * 소유하고 infra가 구현한다(docs/architecture.md — infra vs external). 정렬·현재 키 판정·유예 계산은
 * {@link SigningKeyRing}이 소유하고, 스토어는 원자 추가(CAS)와 적재만 책임진다.
 */
public interface SigningKeyStore {

    /**
     * 저장된 서명키 전부를 반환한다(순서 무관).
     */
    List<StoredSigningKey> loadAll();

    /**
     * 현재 최신 키가 {@code expectedNewestKid}(키 없음 기대는 {@code null})일 때만 새 키를 원자 추가한다.
     * 다른 인스턴스가 먼저 회전·부트스트랩했으면 {@code false}를 반환한다(호출자는 재적재로 수렴).
     */
    boolean append(@Nullable String expectedNewestKid, StoredSigningKey key);

    /**
     * 주어진 키들을 제거한다(유예가 끝난 키 정리).
     */
    void purge(Collection<String> kids);
}
