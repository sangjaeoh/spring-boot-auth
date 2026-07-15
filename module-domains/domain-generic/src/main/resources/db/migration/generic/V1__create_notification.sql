create schema if not exists generic;

-- 알림 발송 이력(DOMAIN_MODEL §3.1). (source_event_id, channel) 유니크는 at-least-once 이벤트 소비의
-- 멱등 발송 키다 — 재전달은 새 행이 아니라 기존 행 재시도(retry_count 증가)로 수렴한다.
create table generic.notification (
    id              uuid         primary key,
    user_id         uuid         not null,
    category        varchar(20)  not null,
    channel         varchar(10)  not null,
    template_id     varchar(100) not null,
    payload         text         not null,
    status          varchar(10)  not null,
    retry_count     int          not null,
    failure_reason  varchar(500),
    sent_at         timestamptz,
    source_event_id uuid         not null,
    created_at      timestamptz  not null,
    updated_at      timestamptz  not null
);

create unique index uq_notification_source_event_channel on generic.notification (source_event_id, channel);
create index idx_notification_user_id_created_at on generic.notification (user_id, created_at desc);
