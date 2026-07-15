package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.entity.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 소셜 연동 요청(로그인 상태). 연동할 소셜 계정의 {@code id_token}을 제출한다.
 */
public record SocialConnectRequest(
        @NotNull SocialProvider provider, @NotBlank String idToken) {}
