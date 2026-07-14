package com.example.auth.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Sha256TokenHasherTest {

    private final Sha256TokenHasher hasher = new Sha256TokenHasher();

    @Test
    void hashesDeterministicallyToHex() {
        String first = hasher.hash("token-value");
        String second = hasher.hash("token-value");

        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]+");
        assertThat(hasher.hash("other")).isNotEqualTo(first);
    }
}
