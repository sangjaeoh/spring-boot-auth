package com.example.auth.app.api.presentation.v1;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청 DTO다. 세션은 기기에 바인딩되므로 기기 식별 블록이 필수다.
 *
 * <p>이메일 형식은 도메인 {@code Email} 규칙과 동치인 패턴으로 경계에서 거른다 — Jakarta {@code @Email}은
 * 점 없는 도메인을 통과시켜 도메인 검증 IAE가 클라이언트 유발 500이 된다.
 */
public record LoginRequest(
        @NotBlank @Size(max = 320) @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "이메일 형식이 올바르지 않습니다")
        String email,
        @NotBlank String password,
        @Valid @NotNull DeviceBindingRequest device) {}
