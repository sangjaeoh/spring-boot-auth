package com.example.auth.domain.user.exception;

import com.example.auth.common.core.exception.BaseException;
import java.util.Map;

/**
 * 탈퇴 후 재가입 쿨다운 중의 가입 시도 거부다(409). 잔여일을 응답 컨텍스트로 노출한다.
 */
public class RejoinCooldownException extends BaseException {

    private final long remainingDays;

    public RejoinCooldownException(long remainingDays) {
        super(UserErrorCode.REJOIN_COOLDOWN);
        this.remainingDays = remainingDays;
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of("remainingDays", remainingDays);
    }
}
