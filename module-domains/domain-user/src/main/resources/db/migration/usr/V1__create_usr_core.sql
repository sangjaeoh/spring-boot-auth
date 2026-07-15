create schema if not exists usr;

-- 회원(User): 캐노니컬 UserId PK. PII(name·birth_date·contact_phone)는 봉투 암호화 varchar로 저장한다
-- (암호화라 DATE 아닌 VARCHAR). 물리 FK 없음(크로스 서비스 분리 대비 — 논리 참조는 인덱스만).
create table usr.users (
    id                 uuid         primary key,
    name               varchar(512) not null,  -- AES-GCM 봉투 암호문(base64)
    birth_date         varchar(512) not null,  -- 암호화 ISO 날짜
    gender             varchar(10)  not null,
    contact_email      varchar(320) not null,  -- 평문(암호화 대상 아님)
    carrier            varchar(10)  not null,
    contact_phone      varchar(512) not null,  -- 암호화 E.164
    contact_phone_bidx varchar(64)  not null,  -- HMAC blind index(pepper 버전 동봉), 전화 동등조회
    status             varchar(20)  not null,
    ci_hash            varchar(128) not null,  -- CI 원장 참조 해시(원문 CI 미저장)
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null
);

-- 전화 동등조회용 blind index. 유니크 아님 — 활성 1인 유일성은 phone이 아니라 CiRegistry가 소유(다음 슬라이스).
create index idx_users_contact_phone_bidx on usr.users (contact_phone_bidx);
-- ci_hash 논리 참조 조회 인덱스. 전역 유니크 hard-enforce는 CiRegistry 애그리거트가 소유(다음 슬라이스).
create index idx_users_ci_hash on usr.users (ci_hash);
