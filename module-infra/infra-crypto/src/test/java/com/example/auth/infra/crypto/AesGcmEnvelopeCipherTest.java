package com.example.auth.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmEnvelopeCipherTest {

    private static final String KEK_V1 = base64Key((byte) 0x11);
    private static final String KEK_V2 = base64Key((byte) 0x22);

    private final AesGcmEnvelopeCipher cipher = new AesGcmEnvelopeCipher(1, "1:" + KEK_V1);

    @Test
    void roundTripsAndCiphertextIsNotPlaintext() {
        String plaintext = "홍길동";

        String ciphertext = cipher.encrypt(plaintext);

        assertThat(ciphertext).isNotEqualTo(plaintext).doesNotContain(plaintext);
        assertThat(cipher.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void embedsActiveKeyVersionAsFirstByte() {
        String ciphertext = cipher.encrypt("+821012345678");

        byte[] blob = Base64.getDecoder().decode(ciphertext);

        assertThat(blob[0]).isEqualTo((byte) 1);
    }

    @Test
    void sameInputYieldsDifferentCiphertextButBothDecrypt() {
        String first = cipher.encrypt("secret");
        String second = cipher.encrypt("secret");

        // random nonce → 암호문은 매번 다르지만(동등조회 불가·blind index로 해결) 둘 다 복호된다.
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("secret");
        assertThat(cipher.decrypt(second)).isEqualTo("secret");
    }

    @Test
    void decryptsOlderVersionCiphertextAfterRotation() {
        // v1으로 암호화한 값을, v1·v2를 모두 아는 회전 후 사이퍼(active=2)가 재암호화 없이 복호한다.
        String v1Ciphertext = cipher.encrypt("legacy-pii");

        AesGcmEnvelopeCipher rotated = new AesGcmEnvelopeCipher(2, "1:" + KEK_V1 + ",2:" + KEK_V2);

        assertThat(rotated.decrypt(v1Ciphertext)).isEqualTo("legacy-pii");
    }

    @Test
    void rejectsCiphertextWhoseKeyVersionIsUnknown() {
        String v2Ciphertext = new AesGcmEnvelopeCipher(2, "2:" + KEK_V2).encrypt("x");

        assertThatThrownBy(() -> cipher.decrypt(v2Ciphertext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("버전 2");
    }

    private static String base64Key(byte fill) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
