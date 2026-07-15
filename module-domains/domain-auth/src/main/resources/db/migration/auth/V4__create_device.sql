-- 디바이스: 로그인 기기 재식별 + 푸시 타겟. 물리 FK 없음(크로스 서비스 분리 대비).
create table auth.device (
    id               uuid         primary key,
    user_id          uuid         not null,
    device_name      varchar(100) not null,
    platform         varchar(20)  not null,
    fingerprint      varchar(255) not null,
    last_ip          varchar(45),
    last_accessed_at timestamptz,
    trusted          boolean      not null,
    push_token       varchar(512),
    push_platform    varchar(20),
    push_enabled     boolean      not null,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null
);

-- 기기 재식별 키. user_id 선두 복합이라 user_id 논리 FK 인덱스를 겸한다(경합 이중등록 backstop).
create unique index uq_device_user_fingerprint on auth.device (user_id, fingerprint);
