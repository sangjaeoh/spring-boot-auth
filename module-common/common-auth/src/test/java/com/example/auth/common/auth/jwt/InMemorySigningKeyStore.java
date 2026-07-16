package com.example.auth.common.auth.jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 테스트용 인메모리 {@link SigningKeyStore}다 — CAS 추가·적재·정리 계약을 그대로 재현한다(내구성만 없음).
 */
public final class InMemorySigningKeyStore implements SigningKeyStore {

    private final List<StoredSigningKey> keys = new ArrayList<>();

    @Override
    public synchronized List<StoredSigningKey> loadAll() {
        return List.copyOf(keys);
    }

    @Override
    public synchronized boolean append(@Nullable String expectedNewestKid, StoredSigningKey key) {
        if (!Objects.equals(newestKid(), expectedNewestKid)) {
            return false;
        }
        keys.add(key);
        return true;
    }

    @Override
    public synchronized void purge(Collection<String> kids) {
        keys.removeIf(stored -> kids.contains(stored.kid()));
    }

    private @Nullable String newestKid() {
        return keys.stream()
                .max(Comparator.comparing(StoredSigningKey::createdAt).thenComparing(StoredSigningKey::kid))
                .map(StoredSigningKey::kid)
                .orElse(null);
    }
}
