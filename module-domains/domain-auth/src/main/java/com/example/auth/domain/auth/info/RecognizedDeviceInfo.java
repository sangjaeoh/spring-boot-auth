package com.example.auth.domain.auth.info;

import java.util.UUID;

/**
 * 로그인 기기 인식 결과다 — 매칭(기존) 또는 등록(신규)된 기기와 신규 여부.
 */
public record RecognizedDeviceInfo(UUID deviceId, boolean newDevice) {}
