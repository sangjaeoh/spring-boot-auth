package com.example.auth.infra.crypto;

import com.example.auth.common.core.crypto.EnvelopeCipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 봉투(envelope) 암호화로 PII를 저장·복원한다.
 *
 * <p>구조: 마스터 키(KEK)가 데이터 키(DEK)를 wrap하고, DEK가 실제 데이터를 암호화한다. 암호문 블롭에 KEK
 * 버전과 wrap된 DEK를 동봉하므로, KEK 회전 시 <b>기존 전 행을 재암호화하지 않고도</b> 옛 버전 KEK로 복호할
 * 수 있다(암호문이 자기 복호에 필요한 모든 것을 담는다). dev는 정적 KEK(설정 주입)를 쓰고, wrap/unwrap은
 * 로컬 KEK로 수행한다 — 실 배포는 이 자리를 KMS 호출로 대체한다(별도 KMS 포트는 2번째 구현 부재로 미도입).
 *
 * <p>데이터키 캐싱: encrypt는 프로세스당 단일 DEK를 재사용하고(생성 시 1회 wrap), decrypt는
 * wrap된 DEK→DEK unwrap 결과를 캐시해 KEK 연산을 아낀다. 단일 DEK + random 96-bit nonce는 dev엔 무해하나,
 * 실 배포는 DEK 회전 정책(NIST 권고 nonce 상한)을 운영에서 문서화한다.
 */
@Component
public class AesGcmEnvelopeCipher implements EnvelopeCipher {

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final int DEK_LEN = 32; // AES-256
    private static final int WRAPPED_DEK_LEN = IV_LEN + DEK_LEN + GCM_TAG_BITS / 8; // kekIv + ct + tag

    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, SecretKey> keks;
    private final int activeVersion;
    private final SecretKey activeKek;

    // wrap된 DEK(base64) → unwrap된 DEK 캐시. 복호 시 KEK unwrap 반복을 아낀다. 상한 없음 — 실무상 마주친
    // 서로 다른 wrapped-DEK 수(≈ KEK 버전 × writer DEK)로 유계이나, 장수 리더·다수 회전 시 상한/LRU는 후속.
    private final Map<String, SecretKey> dekCache = new ConcurrentHashMap<>();

    private final SecretKey currentDek;
    private final byte[] currentWrappedDek;

    public AesGcmEnvelopeCipher(
            @Value("${crypto.envelope.active-version:1}") int activeVersion,
            @Value("${crypto.envelope.keys}") String keysCsv) {
        this.keks = parseKeys(keysCsv);
        this.activeVersion = activeVersion;
        SecretKey kek = keks.get(activeVersion);
        if (kek == null) {
            throw new IllegalStateException("crypto.envelope.keys에 활성 버전 " + activeVersion + " 키가 없습니다.");
        }
        this.activeKek = kek;
        try {
            this.currentDek = generateDek();
            this.currentWrappedDek = wrap(activeKek, currentDek.getEncoded());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("DEK 생성·wrap 실패", e);
        }
        dekCache.put(Base64.getEncoder().encodeToString(currentWrappedDek), currentDek);
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = randomIv();
            byte[] data = gcm(Cipher.ENCRYPT_MODE, currentDek, iv, plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] blob = new byte[1 + WRAPPED_DEK_LEN + IV_LEN + data.length];
            blob[0] = (byte) activeVersion;
            System.arraycopy(currentWrappedDek, 0, blob, 1, WRAPPED_DEK_LEN);
            System.arraycopy(iv, 0, blob, 1 + WRAPPED_DEK_LEN, IV_LEN);
            System.arraycopy(data, 0, blob, 1 + WRAPPED_DEK_LEN + IV_LEN, data.length);
            return Base64.getEncoder().encodeToString(blob);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            byte[] blob = Base64.getDecoder().decode(ciphertext);
            int version = blob[0] & 0xFF;
            SecretKey kek = keks.get(version);
            if (kek == null) {
                throw new IllegalStateException("암호문의 KEK 버전 " + version + " 키가 설정에 없습니다(회전 미배선?).");
            }
            byte[] wrappedDek = Arrays.copyOfRange(blob, 1, 1 + WRAPPED_DEK_LEN);
            SecretKey dek = unwrapCached(kek, wrappedDek);
            byte[] iv = Arrays.copyOfRange(blob, 1 + WRAPPED_DEK_LEN, 1 + WRAPPED_DEK_LEN + IV_LEN);
            byte[] data = Arrays.copyOfRange(blob, 1 + WRAPPED_DEK_LEN + IV_LEN, blob.length);
            return new String(gcm(Cipher.DECRYPT_MODE, dek, iv, data), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("복호 실패", e);
        }
    }

    private SecretKey unwrapCached(SecretKey kek, byte[] wrappedDek) {
        return dekCache.computeIfAbsent(Base64.getEncoder().encodeToString(wrappedDek), key -> {
            try {
                return new SecretKeySpec(unwrap(kek, wrappedDek), "AES");
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("DEK unwrap 실패", e);
            }
        });
    }

    private byte[] wrap(SecretKey kek, byte[] dekBytes) throws GeneralSecurityException {
        byte[] iv = randomIv();
        byte[] ct = gcm(Cipher.ENCRYPT_MODE, kek, iv, dekBytes);
        byte[] out = new byte[IV_LEN + ct.length];
        System.arraycopy(iv, 0, out, 0, IV_LEN);
        System.arraycopy(ct, 0, out, IV_LEN, ct.length);
        return out;
    }

    private byte[] unwrap(SecretKey kek, byte[] wrappedDek) throws GeneralSecurityException {
        byte[] iv = Arrays.copyOfRange(wrappedDek, 0, IV_LEN);
        byte[] ct = Arrays.copyOfRange(wrappedDek, IV_LEN, wrappedDek.length);
        return gcm(Cipher.DECRYPT_MODE, kek, iv, ct);
    }

    private static byte[] gcm(int mode, SecretKey key, byte[] iv, byte[] input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(mode, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(input);
    }

    private SecretKey generateDek() throws GeneralSecurityException {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(DEK_LEN * 8, random);
        return kg.generateKey();
    }

    private byte[] randomIv() {
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);
        return iv;
    }

    private static Map<Integer, SecretKey> parseKeys(String csv) {
        Map<Integer, SecretKey> map = new HashMap<>();
        for (String entry : csv.split(",", -1)) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("crypto.envelope.keys 형식 오류(version:base64 기대): " + entry);
            }
            byte[] keyBytes = Base64.getDecoder().decode(parts[1].trim());
            if (keyBytes.length != DEK_LEN) {
                throw new IllegalStateException("KEK는 32바이트(AES-256)여야 합니다. 실제=" + keyBytes.length);
            }
            int version = Integer.parseInt(parts[0].trim());
            // 버전은 암호문 1바이트에 동봉되므로 0..255. 범위 밖은 wrap돼 구 암호문 복호 불능을 부른다.
            if (version < 0 || version > 255) {
                throw new IllegalStateException("KEK 버전은 0..255여야 합니다(1바이트 동봉). 실제=" + version);
            }
            map.put(version, new SecretKeySpec(keyBytes, "AES"));
        }
        return map;
    }
}
