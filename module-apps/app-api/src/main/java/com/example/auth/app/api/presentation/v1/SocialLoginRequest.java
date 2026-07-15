package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.entity.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 소셜 로그인 요청. 클라이언트가 IdP 인가 플로우로 얻은 {@code id_token}을 제출한다. 미지의 provider
 * 값은 enum 역직렬화가 400으로 차단한다.
 */
public record SocialLoginRequest(
        @NotNull SocialProvider provider, @NotBlank String idToken) {}
