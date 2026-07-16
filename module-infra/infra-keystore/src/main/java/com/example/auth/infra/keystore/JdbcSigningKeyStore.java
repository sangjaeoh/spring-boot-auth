package com.example.auth.infra.keystore;

import com.example.auth.common.auth.jwt.SigningKeyStore;
import com.example.auth.common.auth.jwt.StoredSigningKey;
import com.example.auth.common.core.crypto.EnvelopeCipher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL {@code keyring.signing_key} 기반 서명키 스토어다.
 *
 * <p>개인키 포함 JWK JSON은 봉투암호화({@link EnvelopeCipher})로 저장한다 — DB 덤프·백업이 서명키 원문을
 * 담지 않고, KEK 주입·회전 체계는 PII와 동일 경로를 공유한다. 추가(CAS)는 어드바이저리 잠금 + 최신 kid
 * 재확인 트랜잭션으로 직렬화해 다중 인스턴스의 동시 회전·부트스트랩에서 정확히 한 키만 추가된다.
 */
@Component
public class JdbcSigningKeyStore implements SigningKeyStore {

    // append 직렬화용 어드바이저리 잠금 키(테이블 고정 상수) — 트랜잭션 종료 시 자동 해제.
    private static final String ACQUIRE_APPEND_LOCK_SQL = "select pg_advisory_xact_lock(734691128335141202)";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;
    private final EnvelopeCipher envelopeCipher;

    public JdbcSigningKeyStore(
            JdbcTemplate jdbc, PlatformTransactionManager transactionManager, EnvelopeCipher envelopeCipher) {
        this.jdbc = jdbc;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.envelopeCipher = envelopeCipher;
    }

    @Override
    public List<StoredSigningKey> loadAll() {
        return jdbc.query(
                "select kid, jwk_encrypted, created_at from keyring.signing_key",
                (rs, rowNum) -> new StoredSigningKey(
                        rs.getString("kid"),
                        envelopeCipher.decrypt(rs.getString("jwk_encrypted")),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()));
    }

    @Override
    public boolean append(@Nullable String expectedNewestKid, StoredSigningKey key) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            jdbc.execute(ACQUIRE_APPEND_LOCK_SQL);
            List<String> newest = jdbc.queryForList(
                    "select kid from keyring.signing_key order by created_at desc, kid desc limit 1", String.class);
            @Nullable String newestKid = newest.isEmpty() ? null : newest.get(0);
            if (!Objects.equals(newestKid, expectedNewestKid)) {
                return false;
            }
            jdbc.update(
                    "insert into keyring.signing_key (kid, jwk_encrypted, created_at) values (?, ?, ?)",
                    key.kid(),
                    envelopeCipher.encrypt(key.privateJwkJson()),
                    OffsetDateTime.ofInstant(key.createdAt(), ZoneOffset.UTC));
            return true;
        }));
    }

    @Override
    public void purge(Collection<String> kids) {
        jdbc.batchUpdate(
                "delete from keyring.signing_key where kid = ?",
                kids.stream().map(kid -> new Object[] {kid}).toList());
    }
}
