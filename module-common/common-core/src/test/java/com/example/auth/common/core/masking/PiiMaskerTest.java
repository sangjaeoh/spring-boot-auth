package com.example.auth.common.core.masking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    @Test
    void masksNameKeepingFirstCharacter() {
        assertThat(PiiMasker.maskName("홍길동")).isEqualTo("홍**");
        assertThat(PiiMasker.maskName("김")).isEqualTo("*");
    }

    @Test
    void masksEmailLocalPartKeepingDomain() {
        assertThat(PiiMasker.maskEmail("alice@example.com")).isEqualTo("al***@example.com");
        assertThat(PiiMasker.maskEmail("a@example.com")).isEqualTo("a***@example.com");
        assertThat(PiiMasker.maskEmail("no-at-sign")).isEqualTo("***");
    }

    @Test
    void masksPhoneMiddleDigits() {
        assertThat(PiiMasker.maskPhone("+821012345678")).isEqualTo("+8210****5678");
        assertThat(PiiMasker.maskPhone("12345678")).isEqualTo("****5678");
    }
}
