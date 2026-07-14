package com.example.auth.infra.crypto;

import com.example.auth.common.core.crypto.TokenHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * SHA-256 hex로 토큰 원문을 결정적 해시한다(리프레시 jti 조회·대조 키).
 */
@Component
public class Sha256TokenHasher implements TokenHasher {

    @Override
    public String hash(String rawToken) {
        return HexFormat.of().formatHex(digest().digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 런타임", e);
        }
    }
}
