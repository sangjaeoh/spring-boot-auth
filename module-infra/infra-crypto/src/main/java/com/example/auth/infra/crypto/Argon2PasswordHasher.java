package com.example.auth.infra.crypto;

import com.example.auth.common.core.crypto.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id로 비밀번호를 해시·대조한다(Spring Security Crypto + BouncyCastle).
 *
 * <p>파라미터는 설정으로 외부화한다(기본값은 OWASP 권장 근사). 바운드 실행기 격리·용량 모델은 후속 성능
 * 게이트에서 도입한다(IMPLEMENTATION_PLAN §Phase1a-5).
 */
@Component
public class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder;

    public Argon2PasswordHasher(
            @Value("${auth.password.argon2.salt-length:16}") int saltLength,
            @Value("${auth.password.argon2.hash-length:32}") int hashLength,
            @Value("${auth.password.argon2.parallelism:1}") int parallelism,
            @Value("${auth.password.argon2.memory-kb:19456}") int memoryKb,
            @Value("${auth.password.argon2.iterations:2}") int iterations) {
        this.encoder = new Argon2PasswordEncoder(saltLength, hashLength, parallelism, memoryKb, iterations);
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        return encoder.matches(rawPassword, encodedHash);
    }

    @Override
    public String algorithm() {
        return "ARGON2ID";
    }
}
