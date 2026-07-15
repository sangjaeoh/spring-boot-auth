package com.example.auth.infra.messaging.consume;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 처리 원장({@code msg.processed_event})으로 소비자별 이벤트 1회 처리를 강제한다.
 */
public class ProcessedEventStore {

    private final JdbcTemplate jdbc;

    public ProcessedEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 처리 원장에 기록을 시도한다. 이미 처리된 이벤트면 {@code false}를 반환한다(멱등 스킵 신호).
     *
     * <p>호출 트랜잭션에 참여하므로 소비 실패 롤백 시 기록도 함께 사라진다.
     */
    public boolean tryMarkProcessed(String consumerId, UUID eventId, Instant processedAt) {
        int inserted = jdbc.update(
                "insert into msg.processed_event (consumer_id, event_id, processed_at) values (?, ?, ?)"
                        + " on conflict do nothing",
                consumerId,
                eventId,
                processedAt.atOffset(ZoneOffset.UTC));
        return inserted == 1;
    }
}
