-- RBAC(DOMAIN_MODEL §1.7): 역할·권한 마스터 + 역할-권한 매핑 + 사용자-역할 배정.
-- 물리 FK 없이 논리 FK 인덱스만 생성한다(docs/entity-persistence.md).
create table usr.role (
    id         uuid        primary key,
    name       varchar(30) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index uq_role_name on usr.role (name);

create table usr.permission (
    id         uuid        primary key,
    resource   varchar(50) not null,
    action     varchar(50) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create unique index uq_permission_resource_action on usr.permission (resource, action);

create table usr.role_permission (
    id            uuid        primary key,
    role_id       uuid        not null,
    permission_id uuid        not null,
    created_at    timestamptz not null,
    updated_at    timestamptz not null
);

create unique index uq_role_permission on usr.role_permission (role_id, permission_id);
create index idx_role_permission_permission_id on usr.role_permission (permission_id);

create table usr.user_role (
    id          uuid        primary key,
    user_id     uuid        not null,
    role_id     uuid        not null,
    assigned_at timestamptz not null,
    created_at  timestamptz not null,
    updated_at  timestamptz not null
);

create unique index uq_user_role on usr.user_role (user_id, role_id);
create index idx_user_role_role_id on usr.user_role (role_id);

-- 역할·권한 마스터 시드(고정 UUID — V5 시드와 동일 방침). 관리자 "계정" 시딩은 마이그레이션으로 불가
-- (PII 봉투암호·Argon2 해시가 앱 소유 키라 SQL로 만들 수 없다) — dev/test는 프로비저닝 경로로 시드 후
-- 배정하고, 운영 최초 SUPER_ADMIN 배정은 user_role INSERT 런북이 소유한다.
insert into usr.role (id, name, created_at, updated_at) values
    ('01900000-0000-7000-8000-000000000101', 'USER', now(), now()),
    ('01900000-0000-7000-8000-000000000102', 'ADMIN', now(), now()),
    ('01900000-0000-7000-8000-000000000103', 'SUPER_ADMIN', now(), now());

insert into usr.permission (id, resource, action, created_at, updated_at) values
    ('01900000-0000-7000-8000-000000000201', 'member', 'read', now(), now()),
    ('01900000-0000-7000-8000-000000000202', 'member', 'lock', now(), now()),
    ('01900000-0000-7000-8000-000000000203', 'member', 'force-logout', now(), now()),
    ('01900000-0000-7000-8000-000000000204', 'audit', 'read', now(), now()),
    ('01900000-0000-7000-8000-000000000205', 'role', 'write', now(), now());

-- ADMIN = 회원 운영 + 감사 조회. SUPER_ADMIN = ADMIN 전체 + 역할 변경(role:write).
insert into usr.role_permission (id, role_id, permission_id, created_at, updated_at) values
    ('01900000-0000-7000-8000-000000000301', '01900000-0000-7000-8000-000000000102', '01900000-0000-7000-8000-000000000201', now(), now()),
    ('01900000-0000-7000-8000-000000000302', '01900000-0000-7000-8000-000000000102', '01900000-0000-7000-8000-000000000202', now(), now()),
    ('01900000-0000-7000-8000-000000000303', '01900000-0000-7000-8000-000000000102', '01900000-0000-7000-8000-000000000203', now(), now()),
    ('01900000-0000-7000-8000-000000000304', '01900000-0000-7000-8000-000000000102', '01900000-0000-7000-8000-000000000204', now(), now()),
    ('01900000-0000-7000-8000-000000000305', '01900000-0000-7000-8000-000000000103', '01900000-0000-7000-8000-000000000201', now(), now()),
    ('01900000-0000-7000-8000-000000000306', '01900000-0000-7000-8000-000000000103', '01900000-0000-7000-8000-000000000202', now(), now()),
    ('01900000-0000-7000-8000-000000000307', '01900000-0000-7000-8000-000000000103', '01900000-0000-7000-8000-000000000203', now(), now()),
    ('01900000-0000-7000-8000-000000000308', '01900000-0000-7000-8000-000000000103', '01900000-0000-7000-8000-000000000204', now(), now()),
    ('01900000-0000-7000-8000-000000000309', '01900000-0000-7000-8000-000000000103', '01900000-0000-7000-8000-000000000205', now(), now());

-- 기존 회원 backfill: "가입 시 USER 자동 배정" 규칙의 소급 적용(인증 투영 backfill은 auth V8이 담당).
insert into usr.user_role (id, user_id, role_id, assigned_at, created_at, updated_at)
select gen_random_uuid(), u.id, '01900000-0000-7000-8000-000000000101', now(), now(), now()
from usr.users u;
