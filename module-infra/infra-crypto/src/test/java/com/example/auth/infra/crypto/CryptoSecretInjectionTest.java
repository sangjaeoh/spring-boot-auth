package com.example.auth.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 * 시크릿 주입 경로 계약: 앱 main application.yml은 키 원문 없이 {@code ${CRYPTO_*}} placeholder만 두고,
 * 값은 환경변수(또는 동명 프로퍼티)로 주입된다. 주입 시 빈이 조립되고, 미주입 기동은 placeholder 미해결로
 * fail-fast한다.
 */
class CryptoSecretInjectionTest {

    // 앱 main application.yml과 동일한 placeholder 배선을 재현한다(PSPC는 Boot의 strict 해석과 동일 조건).
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(PropertySourcesPlaceholderConfigurer.class)
            .withUserConfiguration(AesGcmEnvelopeCipher.class, HmacBlindIndexer.class)
            .withPropertyValues(
                    "crypto.envelope.keys=${CRYPTO_ENVELOPE_KEYS}",
                    "crypto.blind-index.peppers=${CRYPTO_BLIND_INDEX_PEPPERS}");

    @Test
    void assemblesCryptoBeansWhenSecretsAreInjected() {
        runner.withPropertyValues(
                        "CRYPTO_ENVELOPE_KEYS=1:" + base64Key((byte) 0x11),
                        "CRYPTO_BLIND_INDEX_PEPPERS=1:" + base64Key((byte) 0x22))
                .run(context -> {
                    AesGcmEnvelopeCipher cipher = context.getBean(AesGcmEnvelopeCipher.class);
                    assertThat(cipher.decrypt(cipher.encrypt("pii"))).isEqualTo("pii");
                    assertThat(context.getBean(HmacBlindIndexer.class).blindIndex("+821012345678"))
                            .startsWith("1:");
                });
    }

    @Test
    void failsFastWhenSecretsAreNotInjected() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("CRYPTO_ENVELOPE_KEYS");
        });
    }

    private static String base64Key(byte fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, fill);
        return Base64.getEncoder().encodeToString(key);
    }
}
