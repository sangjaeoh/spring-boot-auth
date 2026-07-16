package com.example.auth.common.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.auth.jwt.InMemorySigningKeyStore;
import org.junit.jupiter.api.Test;

class JwtCryptoConfigTest {

    private final JwtCryptoConfig config = new JwtCryptoConfig();
    private final InMemorySigningKeyStore store = new InMemorySigningKeyStore();

    @Test
    void rejectsGraceSmallerThanTwiceAccessTtl() {
        assertThatThrownBy(() -> config.signingKeyRing(store, 29, 15)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsGraceAtLeastTwiceAccessTtl() {
        assertThat(config.signingKeyRing(store, 30, 15)).isNotNull();
    }
}
