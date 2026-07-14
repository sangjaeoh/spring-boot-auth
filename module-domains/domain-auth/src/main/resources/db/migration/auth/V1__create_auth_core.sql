create schema if not exists auth;

-- 인증 계정: userId(=UserId) PK, 회원과 1:1. 물리 FK 없음(크로스 서비스 분리 대비).
create table auth.auth_account (
    user_id     uuid         primary key,
    login_email varchar(320) not null,
    lock_state  varchar(20)  not null,
    user_status varchar(20)  not null,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);

-- loginEmail 유니크(정규화 후). 탈퇴 도입(P4) 시 활성-only 부분 유니크로 좁힌다.
create unique index uq_auth_account_login_email on auth.auth_account (login_email);

-- 비밀번호 자격증명: 계정당 0..1, userId PK. 안전 해시만 저장.
create table auth.password_credential (
    user_id       uuid         primary key,
    password_hash varchar(255) not null,
    algorithm     varchar(20)  not null,
    changed_at    timestamptz  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null
);

-- 로그인 이력: append-only. updated_at은 BaseTimeEntity 상속이라 존재하나 실질 불변.
create table auth.login_attempt (
    id             uuid        primary key,
    user_id        uuid,
    result         varchar(20) not null,
    failure_reason varchar(30),
    ip             varchar(45) not null,
    device_id      uuid,
    risk_score     int         not null,
    attempted_at   timestamptz not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null
);

create index idx_login_attempt_user_id_at on auth.login_attempt (user_id, attempted_at desc);
create index idx_login_attempt_at on auth.login_attempt (attempted_at);
