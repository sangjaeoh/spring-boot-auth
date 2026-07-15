package com.example.auth.common.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtCryptoConfigTest {

    private final JwtCryptoConfig config = new JwtCryptoConfig();

    @Test
    void rejectsGraceSmallerThanTwiceAccessTtl() {
        assertThatThrownBy(() -> config.signingKeyRing(29, 15)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsGraceAtLeastTwiceAccessTtl() {
        assertThat(config.signingKeyRing(30, 15)).isNotNull();
    }
}
