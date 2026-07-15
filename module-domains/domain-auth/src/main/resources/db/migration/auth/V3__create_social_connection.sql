-- 소셜 연동: PasswordCredential의 형제 로그인 수단. 물리 FK 없음(크로스 서비스 분리 대비).
create table auth.social_connection (
    id               uuid         primary key,
    user_id          uuid         not null,
    provider         varchar(20)  not null,
    provider_user_id varchar(255) not null,
    provider_email   varchar(320),
    is_private_relay boolean      not null,
    linked_at        timestamptz  not null,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null
);

-- 한 소셜 계정은 하나의 인증계정에만: (provider, subject) 전역 유니크(경합 이중연결 backstop).
create unique index uq_social_connection_provider_subject on auth.social_connection (provider, provider_user_id);

-- 계정당 provider 1개. user_id 선두 복합이라 user_id 논리 FK 인덱스를 겸한다.
create unique index uq_social_connection_user_provider on auth.social_connection (user_id, provider);
