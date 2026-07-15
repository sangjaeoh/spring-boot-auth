package com.example.auth.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class HmacBlindIndexerTest {

    private static final String PEPPER_V1 = base64Pepper((byte) 0x31);

    private final HmacBlindIndexer indexer = new HmacBlindIndexer(1, "1:" + PEPPER_V1);

    @Test
    void producesDeterministicVersionedIndexForEqualityLookup() {
        String phone = "+821012345678";

        String first = indexer.blindIndex(phone);
        String second = indexer.blindIndex(phone);

        // 결정적: 같은 입력 → 같은 출력(동등조회 성립).
        assertThat(first).isEqualTo(second);
        // pepper 버전 동봉 + 평문 미포함.
        assertThat(first).startsWith("1:").doesNotContain(phone);
    }

    @Test
    void differentInputsYieldDifferentIndexes() {
        assertThat(indexer.blindIndex("+821012345678")).isNotEqualTo(indexer.blindIndex("+821087654321"));
    }

    @Test
    void differentPepperYieldsDifferentIndexForSameValue() {
        HmacBlindIndexer rotated = new HmacBlindIndexer(2, "2:" + base64Pepper((byte) 0x32));

        // pepper가 바뀌면 같은 값이라도 인덱스가 달라진다(회전 시 재인덱싱 필요성의 근거).
        assertThat(rotated.blindIndex("+821012345678")).isNotEqualTo(indexer.blindIndex("+821012345678"));
    }

    private static String base64Pepper(byte fill) {
        byte[] pepper = new byte[32];
        Arrays.fill(pepper, fill);
        return Base64.getEncoder().encodeToString(pepper);
    }
}
