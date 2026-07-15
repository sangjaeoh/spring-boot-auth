package com.example.auth.infra.messaging.consume;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.messaging.IntegrationEvent;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 소비 실패 이벤트를 DLQ({@code msg.dead_letter_event})에 내구 보관하고 재시도 대상을 관리한다.
 */
public class DeadLetterStore {

    /**
     * 재시도에 필요한 DLQ 행 스냅샷이다.
     */
    public record DeadLetter(UUID id, String consumerId, String eventType, String payload, int attempts) {}

    private final JdbcTemplate jdbc;

    public DeadLetterStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 실패 이벤트를 적재한다. 같은 {@code (consumerId, eventId)} 행이 이미 있으면 유지한다(중복 실패 수렴).
     */
    public void enqueue(String consumerId, IntegrationEvent event, String payload, String error, Instant nextRetryAt) {
        Instant now = Instant.now();
        jdbc.update(
                "insert into msg.dead_letter_event"
                        + " (id, consumer_id, event_id, event_type, payload, last_error, attempts, next_retry_at,"
                        + " created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, 1, ?, ?, ?)"
                        + " on conflict (consumer_id, event_id) do nothing",
                UuidV7Generator.generate(),
                consumerId,
                event.eventId(),
                event.eventType(),
                payload,
                error,
                nextRetryAt.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC));
    }

    /**
     * 재시도 기한이 도래한 행을 오래된 순으로 조회한다.
     */
    public List<DeadLetter> findDue(Instant now, int limit) {
        return jdbc.query(
                "select id, consumer_id, event_type, payload, attempts from msg.dead_letter_event"
                        + " where next_retry_at <= ? order by next_retry_at limit ?",
                (rs, rowNum) -> new DeadLetter(
                        rs.getObject("id", UUID.class),
                        rs.getString("consumer_id"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getInt("attempts")),
                now.atOffset(ZoneOffset.UTC),
                limit);
    }

    /**
     * 재시도 성공한 행을 제거한다.
     */
    public void purge(UUID id) {
        jdbc.update("delete from msg.dead_letter_event where id = ?", id);
    }

    /**
     * 재시도 실패를 기록한다 — 시도 횟수를 올리고 다음 재시도 시각을 미룬다.
     */
    public void recordFailure(UUID id, int attempts, String error, Instant nextRetryAt) {
        jdbc.update(
                "update msg.dead_letter_event set attempts = ?, last_error = ?, next_retry_at = ?, updated_at = ?"
                        + " where id = ?",
                attempts,
                error,
                nextRetryAt.atOffset(ZoneOffset.UTC),
                Instant.now().atOffset(ZoneOffset.UTC),
                id);
    }
}
