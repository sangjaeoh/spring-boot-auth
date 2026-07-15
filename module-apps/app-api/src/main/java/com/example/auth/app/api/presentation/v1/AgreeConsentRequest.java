package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.TermsType;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * 이용 중 동의 요청이다. {@code channel}은 마케팅 동의의 채널 한정 선택에만 쓴다(생략 시 전 채널).
 */
public record AgreeConsentRequest(
        @NotNull TermsType termsType,
        @NotNull Integer termsVersion,
        @Nullable NotificationChannel channel) {}
