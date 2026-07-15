-- CI 원장(CiRegistry): ci_hash 전역 유일로 동일인 다중 활성가입 봉쇄. 유니크 인덱스가 hard-enforce의
-- 정본이다 — 가입 중 soft-check(읽기)는 조기 판정일 뿐이고, link는 CreateUser 단일 트랜잭션에서 수행된다.
create table usr.ci_registry (
    id             uuid         primary key,
    ci_hash        varchar(128) not null,
    status         varchar(30)  not null,
    linked_user_id uuid,                    -- ACTIVE_LINKED에서만 존재. 물리 FK 없음
    first_seen_at  timestamptz  not null,
    withdrawn_at   timestamptz,             -- 탈퇴 tombstone 전이 시각(P4)
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null
);

create unique index uq_ci_registry_ci_hash on usr.ci_registry (ci_hash);
-- 논리 FK 인덱스(물리 FK 금지).
create index idx_ci_registry_linked_user_id on usr.ci_registry (linked_user_id);
