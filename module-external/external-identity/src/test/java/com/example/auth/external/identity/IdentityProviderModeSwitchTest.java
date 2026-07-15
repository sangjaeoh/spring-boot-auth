package com.example.auth.external.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.domain.user.port.IdentityVerificationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Mock↔실(NICE) 어댑터 전환이 설정 스위치만으로 이뤄짐을 검증한다 — 두 어댑터 모두 domain-user 소유
 * {@code IdentityVerificationProvider} 포트의 구현이라 전환에 도메인 변경이 없다.
 */
class IdentityProviderModeSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MockIdentityVerificationProvider.class, NiceIdentityVerificationProvider.class);

    @Test
    @DisplayName("mode 미설정이면 Mock 어댑터가 선택된다(dev/test 기본)")
    void defaultsToMock() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(IdentityVerificationProvider.class);
            assertThat(context.getBean(IdentityVerificationProvider.class))
                    .isInstanceOf(MockIdentityVerificationProvider.class);
        });
    }

    @Test
    @DisplayName("mode=nice면 실 기관 어댑터가 선택된다(도메인 무변경 스위치)")
    void switchesToNiceByProperty() {
        runner.withPropertyValues(
                        "user.identity-verification.mode=nice",
                        "user.identity-verification.nice.site-code=SITE",
                        "user.identity-verification.nice.client-id=client",
                        "user.identity-verification.nice.client-secret=secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(IdentityVerificationProvider.class);
                    assertThat(context.getBean(IdentityVerificationProvider.class))
                            .isInstanceOf(NiceIdentityVerificationProvider.class);
                });
    }

    @Test
    @DisplayName("mode=nice인데 기관 자격 설정이 비어 있으면 기동에 실패한다(fail-fast)")
    void failsFastWhenNiceCredentialsMissing() {
        runner.withPropertyValues("user.identity-verification.mode=nice")
                .run(context -> assertThat(context).hasFailed());
    }
}
