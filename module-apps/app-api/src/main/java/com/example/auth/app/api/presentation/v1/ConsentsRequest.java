package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.user.entity.TermsType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/**
 * 온보딩 동의 제출. 미지의 termsType 값은 enum 역직렬화가 400으로 차단한다.
 */
public record ConsentsRequest(
        @NotNull UUID registrationId,
        @NotBlank String onboardingToken,
        @NotEmpty List<@Valid ConsentItem> consents) {

    public ConsentsRequest {
        consents = consents == null ? List.of() : List.copyOf(consents);
    }

    public record ConsentItem(
            @NotNull TermsType termsType, @Positive int version) {}
}
