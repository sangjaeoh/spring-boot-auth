package com.example.auth.external.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VendorNotificationSenderTest {

    @Test
    void rejectsVendorModeWithoutRequiredKeys() {
        assertThatThrownBy(() -> new VendorNotificationSender("", "", "", "", "", "", "", "", 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generic.notification.email.base-url")
                .hasMessageContaining("generic.notification.push.api-key");
    }
}
