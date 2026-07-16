-- 토큰 roles 클레임의 원천 투영(RoleChanged 소비로 갱신 — userStatus 투영과 동일 패턴). 로그인·재발급
-- 핫패스가 usr 스키마를 읽지 않게 한다. default 'USER'가 기존 계정을 backfill한다(usr V13의 user_role
-- backfill과 정합).
alter table auth.auth_account add column roles varchar(200) not null default 'USER';

-- 투영 반영 시각 — 재전달·역순 이벤트(DLQ 재시도)의 단조 가드(occurredAt 기준).
alter table auth.auth_account add column roles_applied_at timestamptz;
