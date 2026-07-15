package com.example.auth.infra.crypto;

import com.example.auth.common.core.crypto.PasswordHasher;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id로 비밀번호를 해시·대조한다(Spring Security Crypto + BouncyCastle).
 *
 * <p>파라미터는 설정으로 외부화한다(기본값은 OWASP 권장 근사). 동시 Argon2 실행은 {@link ConcurrencyLimiter}로
 * 상한해 로그인 폭주 시 메모리 폭주·CPU 스래싱을 막고, 포화 시 부하를 차단한다(503).
 *
 * <p>용량 모델: peak Argon2 힙 ≈ {@code max-concurrent × memory-kb}(auto=가용 코어수 → 코어 × 약 19MB).
 * 대기자는 permit 획득 전까지 Argon2 메모리를 점유하지 않는다. 동시 상한 = 동시 CPU 포화 코어 수.
 */
@Component
public class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder;
    private final ConcurrencyLimiter limiter;

    public Argon2PasswordHasher(
            @Value("${auth.password.argon2.salt-length:16}") int saltLength,
            @Value("${auth.password.argon2.hash-length:32}") int hashLength,
            @Value("${auth.password.argon2.parallelism:1}") int parallelism,
            @Value("${auth.password.argon2.memory-kb:19456}") int memoryKb,
            @Value("${auth.password.argon2.iterations:2}") int iterations,
            @Value("${auth.password.hashing.max-concurrent:0}") int maxConcurrent,
            @Value("${auth.password.hashing.acquire-timeout-ms:500}") long acquireTimeoutMs) {
        this.encoder = new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memoryKb, iterations);
        // 0 = auto: Argon2는 해시당 1코어를 포화하는 CPU-바운드라 가용 코어수를 상한으로 둔다(오버섭스크립션 회피).
        int permits = maxConcurrent > 0 ? maxConcurrent : Runtime.getRuntime().availableProcessors();
        this.limiter = new ConcurrencyLimiter(permits, Duration.ofMillis(acquireTimeoutMs));
        // 세마포어는 동시 실행 수(=메모리·CPU)를 상한하나 가상 스레드 캐리어 피닝은 막지 못한다 —
        // spring.threads.virtual.enabled 활성 시엔 전용 플랫폼-스레드 실행기라야 캐리어를 격리한다.
    }

    @Override
    public String hash(String rawPassword) {
        return limiter.call(() -> encoder.encode(rawPassword));
    }

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        return limiter.call(() -> encoder.matches(rawPassword, encodedHash));
    }

    @Override
    public String algorithm() {
        return "ARGON2ID";
    }
}
