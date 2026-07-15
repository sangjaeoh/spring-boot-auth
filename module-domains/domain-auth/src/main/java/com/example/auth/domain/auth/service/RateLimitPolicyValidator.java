package com.example.auth.domain.auth.service;

import com.example.auth.common.core.crypto.TokenHasher;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.NotificationChannel;
import com.example.auth.domain.auth.port.RateLimitStore;
import java.time.Duration;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 민감 엔드포인트 레이트리밋 정책({@code RateLimitPolicy})을 강제한다 — 로그인(IP·계정)·재설정
 * 개시(IP·대상)·인증코드 발급(재발송 쿨다운·채널별 일일 한도).
 *
 * <p>키의 이메일·전화 원문은 결정적 해시로만 저장소에 넘긴다(Redis 키 PII 비노출). 재설정 개시
 * 제한은 계정 존재와 무관하게 균일 적용한다(열거 저항 — 존재 계정만 제한하면 429 차이가 존재
 * 신호가 된다).
 */
@Service
public class RateLimitPolicyValidator {

    private final RateLimitStore store;
    private final TokenHasher tokenHasher;
    private final int loginIpLimit;
    private final int loginAccountLimit;
    private final Duration loginWindow;
    private final int passwordResetLimit;
    private final Duration passwordResetWindow;
    private final Duration resendCooldown;
    private final int dailyLimitEmail;
    private final int dailyLimitSms;

    public RateLimitPolicyValidator(
            RateLimitStore store,
            TokenHasher tokenHasher,
            @Value("${auth.rate-limit.login.ip-limit:30}") int loginIpLimit,
            @Value("${auth.rate-limit.login.account-limit:15}") int loginAccountLimit,
            @Value("${auth.rate-limit.login.window-seconds:60}") long loginWindowSeconds,
            @Value("${auth.rate-limit.password-reset.limit:5}") int passwordResetLimit,
            @Value("${auth.rate-limit.password-reset.window-seconds:900}") long passwordResetWindowSeconds,
            @Value("${auth.verification.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${auth.verification.daily-limit-email:10}") int dailyLimitEmail,
            @Value("${auth.verification.daily-limit-sms:5}") int dailyLimitSms) {
        this.store = store;
        this.tokenHasher = tokenHasher;
        this.loginIpLimit = loginIpLimit;
        this.loginAccountLimit = loginAccountLimit;
        this.loginWindow = Duration.ofSeconds(loginWindowSeconds);
        this.passwordResetLimit = passwordResetLimit;
        this.passwordResetWindow = Duration.ofSeconds(passwordResetWindowSeconds);
        this.resendCooldown = Duration.ofSeconds(resendCooldownSeconds);
        this.dailyLimitEmail = dailyLimitEmail;
        this.dailyLimitSms = dailyLimitSms;
    }

    /**
     * 로그인 시도를 IP·계정 식별자 기준으로 제한한다(식별자 미상인 흐름은 IP만).
     *
     * @throws AuthException 한도 초과 시(429)
     */
    public void checkLogin(String ip, @Nullable String loginEmail) {
        enforce("login:ip:" + ip, loginIpLimit, loginWindow);
        if (loginEmail != null) {
            enforce("login:acct:" + hash(loginEmail.trim().toLowerCase(Locale.ROOT)), loginAccountLimit, loginWindow);
        }
    }

    /**
     * 비밀번호 재설정 개시를 IP·대상 기준으로 제한한다(계정 존재와 무관하게 균일).
     *
     * @throws AuthException 한도 초과 시(429)
     */
    public void checkPasswordResetInitiate(String ip, String targetEmail) {
        enforce("reset:ip:" + ip, passwordResetLimit, passwordResetWindow);
        enforce(
                "reset:target:" + hash(targetEmail.trim().toLowerCase(Locale.ROOT)),
                passwordResetLimit,
                passwordResetWindow);
    }

    /**
     * 인증코드 발급을 제한한다 — 대상별 재발송 쿨다운과 채널별 일일 발송 한도.
     *
     * @throws AuthException 쿨다운 중이거나 일일 한도 초과 시(429)
     */
    public void checkCodeIssue(NotificationChannel channel, String target) {
        String targetHash = hash(channel.name() + ":" + target);
        if (resendCooldown.isPositive() && !store.tryAcquire("code:cd:" + targetHash, resendCooldown)) {
            throw new AuthException(AuthErrorCode.RATE_LIMITED);
        }
        enforce("code:daily:" + targetHash, dailyLimit(channel), Duration.ofDays(1));
    }

    private void enforce(String key, int limit, Duration window) {
        if (limit <= 0) {
            return;
        }
        if (store.increment(key, window) > limit) {
            throw new AuthException(AuthErrorCode.RATE_LIMITED);
        }
    }

    private int dailyLimit(NotificationChannel channel) {
        return channel == NotificationChannel.SMS ? dailyLimitSms : dailyLimitEmail;
    }

    private String hash(String value) {
        return tokenHasher.hash(value);
    }
}
