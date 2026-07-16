-- 감사 로그(DOMAIN_MODEL §3.2): append-only WORM. before/after는 관리자 행위·권한 변경의 전/후 값(JSON),
-- context는 부가 컨텍스트(JSON)다. updated_at은 BaseTimeEntity 상속이라 존재하나 실질 불변.
create table generic.audit_log (
    id           uuid         primary key,
    actor        varchar(100) not null,
    action       varchar(100) not null,
    target       varchar(100),
    before_value text,
    after_value  text,
    context      text,
    occurred_at  timestamptz  not null,
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null
);

create index idx_audit_log_target_occurred_at on generic.audit_log (target, occurred_at desc);
create index idx_audit_log_occurred_at on generic.audit_log (occurred_at desc);

-- WORM 강제의 DB 백스톱(리포지토리 표면 배제의 이중 방어) — raw SQL writer까지 차단한다. 보존창(2년)
-- 경과 후 PII crypto-shred가 배선되면 그 마이그레이션이 허용 범위를 좁혀 조정한다.
create function generic.audit_log_block_mutation() returns trigger
language plpgsql as $$
begin
    raise exception 'audit_log is append-only (WORM)';
end;
$$;

create trigger trg_audit_log_worm
    before update or delete on generic.audit_log
    for each row execute function generic.audit_log_block_mutation();
