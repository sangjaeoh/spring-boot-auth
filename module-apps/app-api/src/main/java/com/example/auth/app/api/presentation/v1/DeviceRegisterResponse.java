package com.example.auth.app.api.presentation.v1;

import java.util.UUID;

/**
 * 기기 등록 응답. 등록된 기기의 ID다.
 */
public record DeviceRegisterResponse(UUID deviceId) {}
