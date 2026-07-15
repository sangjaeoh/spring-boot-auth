package com.example.auth.app.api.event.listener;

import com.example.auth.common.messaging.IntegrationEventConsumer;
import com.example.auth.domain.auth.event.DeviceDeleted;
import com.example.auth.domain.auth.service.SessionProcessor;
import org.springframework.stereotype.Component;

/**
 * 기기 삭제 이벤트를 소비해 해당 기기에 바인딩된 세션을 종료한다(DOMAIN_MODEL §2.5 교차). 세션 스토어
 * 불가 시 예외가 전파되어 DLQ 재시도로 수렴한다(무효화 유실 없음).
 */
@Component
public class DeviceDeletedListener implements IntegrationEventConsumer<DeviceDeleted> {

    private final SessionProcessor sessionProcessor;

    public DeviceDeletedListener(SessionProcessor sessionProcessor) {
        this.sessionProcessor = sessionProcessor;
    }

    @Override
    public String consumerId() {
        return "auth-session.device-deleted";
    }

    @Override
    public Class<DeviceDeleted> eventType() {
        return DeviceDeleted.class;
    }

    @Override
    public void consume(DeviceDeleted event) {
        sessionProcessor.revokeByDevice(event.userId(), event.deviceId());
    }
}
