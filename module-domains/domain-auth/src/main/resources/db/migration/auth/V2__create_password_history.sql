-- 비밀번호 이력: password_credential 애그리거트의 자식(최근 N 재사용 금지 대조용). 물리 FK 없음(NO_CONSTRAINT).
create table auth.password_history (
    id            uuid         primary key,
    user_id       uuid         not null,
    password_hash varchar(255) not null,
    algorithm     varchar(20)  not null,
    changed_at    timestamptz  not null,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null
);

-- 논리 FK 인덱스: @OneToMany 컬렉션 로딩(where user_id = ?)이 풀스캔이 되지 않게 한다.
create index idx_password_history_user_id on auth.password_history (user_id);
