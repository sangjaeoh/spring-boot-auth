package com.example.auth.app.api.presentation.v1;

import com.example.auth.domain.auth.entity.DevicePlatform;

/**
 * 로그인 요청 기기 블록 픽스처다.
 */
public final class DeviceBindingRequestFixture {

    private DeviceBindingRequestFixture() {}

    public static DeviceBindingRequest webDevice() {
        return webDevice("fp-test-default");
    }

    public static DeviceBindingRequest webDevice(String fingerprint) {
        return new DeviceBindingRequest(fingerprint, "테스트 브라우저", DevicePlatform.WEB);
    }
}
