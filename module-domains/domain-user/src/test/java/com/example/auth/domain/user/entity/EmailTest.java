package com.example.auth.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void normalizesToLowerCaseAndTrims() {
        assertThat(Email.of("  Hong@Example.COM ").value()).isEqualTo("hong@example.com");
    }

    @Test
    void rejectsMalformed() {
        assertThatThrownBy(() -> Email.of("not-an-email")).isInstanceOf(IllegalArgumentException.class);
    }
}
