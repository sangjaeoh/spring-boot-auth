package com.example.auth.infra.messaging.consume;

import com.example.auth.common.messaging.IntegrationEvent;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 통합 이벤트 페이로드를 공개 스키마 JSON으로 직렬화·역직렬화한다(DLQ 내구 보관용).
 *
 * <p>웹 계층 ObjectMapper 설정과 결합하지 않도록 전용 매퍼를 쓴다. 미지 필드는 무시한다 — 구버전 소비자가
 * 신버전 페이로드를 읽어도 깨지지 않는다(스키마 전방 호환).
 */
public class EventPayloadCodec {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * 이벤트를 JSON 문자열로 직렬화한다.
     */
    public String serialize(IntegrationEvent event) {
        return mapper.writeValueAsString(event);
    }

    /**
     * JSON 문자열을 이벤트 타입으로 역직렬화한다.
     */
    public <E extends IntegrationEvent> E deserialize(String payload, Class<E> type) {
        return mapper.readValue(payload, type);
    }
}
