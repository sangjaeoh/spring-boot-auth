package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.NotificationPreferenceFacade;
import com.example.auth.common.web.security.AuthUser;
import com.example.auth.domain.user.entity.NotificationCategory;
import com.example.auth.domain.user.entity.NotificationChannel;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 수신 설정 API(채널×카테고리 opt-in/out — SECURITY는 연락 채널 최소 1개 유지 강제).
 */
@RestController
@RequestMapping("/users/me/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceFacade notificationPreferenceFacade;

    public NotificationPreferenceController(NotificationPreferenceFacade notificationPreferenceFacade) {
        this.notificationPreferenceFacade = notificationPreferenceFacade;
    }

    /**
     * 수신 설정 매트릭스를 반환한다.
     */
    @GetMapping
    public NotificationPreferenceResponse get(@AuthenticationPrincipal AuthUser user) {
        return notificationPreferenceFacade.get(user.userId());
    }

    /**
     * 해당 채널×카테고리 수신을 허용/거부한다(SECURITY 마지막 연락 채널 해제는 400).
     */
    @PutMapping("/{channel}/{category}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable NotificationChannel channel,
            @PathVariable NotificationCategory category,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request) {
        notificationPreferenceFacade.update(user.userId(), channel, category, request.allowed());
    }
}
