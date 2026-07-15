package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 본인인증 요청(주장된 정체성). 미지의 gender·carrier 값은 enum 역직렬화가 400으로 차단한다.
 */
public record IdentityVerifyRequest(
        @NotNull UUID registrationId,
        @NotBlank String onboardingToken,
        @NotBlank String name,
        @NotNull @Past LocalDate birthDate,
        @NotNull Gender gender,
        @NotNull Carrier carrier,

        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "E.164 형식이어야 합니다")
        String phone) {}
