package com.example.auth.app.api.presentation.v1;

import jakarta.validation.constraints.NotNull;

/**
 * OS 푸시 권한 반영 요청이다.
 */
public record DevicePushPermissionRequest(@NotNull Boolean enabled) {}
