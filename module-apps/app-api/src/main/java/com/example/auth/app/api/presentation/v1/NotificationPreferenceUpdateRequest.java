package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotNull;

/**
 * 알림 수신 설정 변경 요청이다.
 */
public record NotificationPreferenceUpdateRequest(@NotNull Boolean allowed) {}
