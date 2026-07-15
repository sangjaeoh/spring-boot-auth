-- 현재 동의 스냅샷 읽기모델(ConsentState): consent_record append 로그의 fold 캐시.
-- (user_id, terms_type) 유니크 — 회원×유형당 현재값 1행. 로그와 같은 트랜잭션에서 갱신된다.
create table usr.consent_state (
    id             uuid        primary key,
    user_id        uuid        not null,
    terms_type     varchar(30) not null,
    current_action varchar(10) not null,
    agreed_version int,                     -- WITHDRAW면 null
    created_at     timestamptz not null,
    updated_at     timestamptz not null
);

-- 유니크가 (user_id, ...) 선두라 회원별 조회 인덱스를 겸한다(논리 FK 인덱스, 물리 FK 금지).
create unique index uq_consent_state_user_terms on usr.consent_state (user_id, terms_type);
