-- 본인인증(IdentityVerification): 외부 실명확인의 요청·결과. 결과 PII(name·birth_date·phone·di)는
-- 봉투 암호화 varchar, CI는 원문 없이 salted HMAC(ci_hash)만. 결과 컬럼은 VERIFIED에서만 채워져 nullable.
create table usr.identity_verification (
    id           uuid         primary key,
    user_id      uuid,                     -- CreateUser 시 연결(진행 중 null). 물리 FK 없음
    provider     varchar(10)  not null,
    status       varchar(20)  not null,
    name         varchar(512),             -- AES-GCM 봉투 암호문
    birth_date   varchar(512),             -- 암호화 ISO 날짜
    gender       varchar(10),
    carrier      varchar(10),
    phone        varchar(512),             -- 암호화 E.164
    ci_hash      varchar(128),             -- salted HMAC(pepper 버전 동봉). 원문 CI 미저장
    di           varchar(512),             -- 암호화 사이트별 식별정보
    requested_at timestamptz  not null,
    verified_at  timestamptz,
    expires_at   timestamptz  not null,
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null
);

-- 논리 FK 인덱스(물리 FK 금지 — docs/entity-persistence.md).
create index idx_identity_verification_user_id on usr.identity_verification (user_id);
-- CI 기준 감사·조회. 유일성은 이 테이블이 아니라 ci_registry가 소유(시도 이력은 다행 가능).
create index idx_identity_verification_ci_hash on usr.identity_verification (ci_hash);
