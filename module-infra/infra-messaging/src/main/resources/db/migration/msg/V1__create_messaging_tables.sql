create schema if not exists msg;

-- 처리 원장(디둡): (consumer_id, event_id) PK가 소비자별 이벤트 1회 처리를 강제한다.
-- 소비 트랜잭션과 같은 트랜잭션에서 insert되므로 같은 DataSource 쓰기와 원자 커밋된다.
create table msg.processed_event (
    consumer_id  varchar(100) not null,
    event_id     uuid         not null,
    processed_at timestamptz  not null,
    primary key (consumer_id, event_id)
);

-- 소비 실패 DLQ: 재시도 성공까지 내구 보관. payload는 통합 이벤트 공개 스키마 JSON.
-- (consumer_id, event_id) 유니크 — 같은 이벤트의 중복 실패는 최초 행 하나로 수렴한다.
create table msg.dead_letter_event (
    id            uuid         primary key,
    consumer_id   varchar(100) not null,
    event_id      uuid         not null,
    event_type    varchar(200) not null,
    payload       text         not null,
    last_error    text         not null,
    attempts      int          not null,
    next_retry_at timestamptz  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null
);

create unique index uq_dead_letter_event_consumer_event on msg.dead_letter_event (consumer_id, event_id);
create index idx_dead_letter_event_next_retry_at on msg.dead_letter_event (next_retry_at);
