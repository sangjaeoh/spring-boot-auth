package com.example.auth.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher(16, 32, 1, 19456, 2, 2, 1000);

    @Test
    void hashesAndMatches() {
        String encoded = hasher.hash("secret123");

        assertThat(encoded).isNotEqualTo("secret123");
        assertThat(hasher.matches("secret123", encoded)).isTrue();
        assertThat(hasher.matches("wrong", encoded)).isFalse();
        assertThat(hasher.algorithm()).isEqualTo("ARGON2ID");
    }
}
