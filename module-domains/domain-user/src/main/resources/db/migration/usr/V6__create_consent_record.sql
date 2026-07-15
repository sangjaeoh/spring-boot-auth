-- 동의 이력(ConsentRecord): append-only 불변 로그(update/delete 없음 — DOMAIN_MODEL §1.5). 가입 최초
-- 동의는 CreateUser 트랜잭션에서 userId와 함께 append된다. channel(마케팅 채널)·ConsentState fold
-- 읽기모델은 동의 정식화(P4)에서 추가한다.
create table usr.consent_record (
    id            uuid        primary key,
    user_id       uuid        not null,      -- 회원 논리 참조. 물리 FK 없음
    terms_type    varchar(30) not null,
    terms_version int         not null,
    action        varchar(10) not null,
    at            timestamptz not null,      -- 동의/철회 시각(도메인 세팅)
    created_at    timestamptz not null,
    updated_at    timestamptz not null
);

-- 논리 FK 인덱스(물리 FK 금지). 회원별 이력 fold 조회 경로.
create index idx_consent_record_user_id on usr.consent_record (user_id);
