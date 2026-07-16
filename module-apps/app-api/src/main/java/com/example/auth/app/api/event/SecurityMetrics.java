package com.example.auth.app.api.event;

import com.example.auth.domain.auth.event.AccountLocked;
import com.example.auth.domain.auth.event.LoggedIn;
import com.example.auth.domain.auth.event.LoginFailed;
import com.example.auth.domain.auth.event.RefreshReuseDetected;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 보안 탐지 지표를 Micrometer 카운터로 계측한다(재사용 감지 급증·잠금율 알람의 원천 —
 * 지표·SLO 정의는 docs/ops/observability-slo.md).
 *
 * <p>로그인 지연(p99)은 별도 계측 없이 {@code http.server.requests} URI별 히스토그램이 소유한다.
 */
@Component
public class SecurityMetrics {

    private final Counter loginSuccess;
    private final Counter refreshReuseDetected;
    private final Counter accountLocked;
    private final MeterRegistry registry;

    public SecurityMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.loginSuccess =
                Counter.builder("auth.login.success").description("로그인 성공 수").register(registry);
        this.refreshReuseDetected = Counter.builder("auth.refresh.reuse.detected")
                .description("리프레시 재사용(탈취) 감지 수 — 급증 알람 대상")
                .register(registry);
        this.accountLocked = Counter.builder("auth.account.locked")
                .description("연속 실패 임계 초과 일시 잠금 수 — 잠금율 알람 대상")
                .register(registry);
    }

    @EventListener
    public void onLoggedIn(LoggedIn event) {
        loginSuccess.increment();
    }

    @EventListener
    public void onLoginFailed(LoginFailed event) {
        // reason은 enum이라 태그 카디널리티가 유계다.
        Counter.builder("auth.login.failure")
                .description("로그인 실패 수(사유별)")
                .tag("reason", event.reason().name())
                .register(registry)
                .increment();
    }

    @EventListener
    public void onRefreshReuseDetected(RefreshReuseDetected event) {
        refreshReuseDetected.increment();
    }

    @EventListener
    public void onAccountLocked(AccountLocked event) {
        accountLocked.increment();
    }
}
