package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.DeviceFacade;
import com.example.auth.common.web.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 기기 API(등록·목록·신뢰/해제·푸시 토큰/권한·등록 해제).
 */
@RestController
@RequestMapping("/auth/devices")
public class DeviceController {

    private final DeviceFacade deviceFacade;

    public DeviceController(DeviceFacade deviceFacade) {
        this.deviceFacade = deviceFacade;
    }

    /**
     * 기기를 명시 등록한다. 같은 지문의 기기가 이미 있으면 409다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceRegisterResponse register(
            @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody DeviceRegisterRequest request,
            HttpServletRequest httpRequest) {
        return new DeviceRegisterResponse(deviceFacade.register(user.userId(), request, httpRequest.getRemoteAddr()));
    }

    /**
     * 내 기기 목록을 최근 접속 순으로 반환한다.
     */
    @GetMapping
    public List<DeviceResponse> devices(@AuthenticationPrincipal AuthUser user) {
        return deviceFacade.devices(user.userId());
    }

    /**
     * 기기를 신뢰로 표시한다.
     */
    @PutMapping("/{deviceId}/trust")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trust(@AuthenticationPrincipal AuthUser user, @PathVariable UUID deviceId) {
        deviceFacade.trust(user.userId(), deviceId);
    }

    /**
     * 기기 신뢰를 해제한다.
     */
    @DeleteMapping("/{deviceId}/trust")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untrust(@AuthenticationPrincipal AuthUser user, @PathVariable UUID deviceId) {
        deviceFacade.untrust(user.userId(), deviceId);
    }

    /**
     * 푸시 토큰과 전송 플랫폼을 등록한다(재등록은 교체).
     */
    @PutMapping("/{deviceId}/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerPushToken(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable UUID deviceId,
            @Valid @RequestBody DevicePushTokenRequest request) {
        deviceFacade.registerPushToken(user.userId(), deviceId, request.token(), request.platform());
    }

    /**
     * OS 푸시 권한 상태를 반영한다.
     */
    @PutMapping("/{deviceId}/push-permission")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reflectPushPermission(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable UUID deviceId,
            @Valid @RequestBody DevicePushPermissionRequest request) {
        deviceFacade.reflectPushPermission(user.userId(), deviceId, request.enabled());
    }

    /**
     * 기기를 등록 해제한다. 해당 기기의 세션은 즉시 종료된다.
     */
    @DeleteMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deregister(@AuthenticationPrincipal AuthUser user, @PathVariable UUID deviceId) {
        deviceFacade.deregister(user.userId(), deviceId);
    }
}
