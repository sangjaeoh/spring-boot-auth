-- 잠금 시각·사유(DOMAIN_MODEL §2.1). locked_at은 일시 잠금 쿨다운 경과 판정 기준이다.
alter table auth.auth_account add column locked_at timestamptz;
alter table auth.auth_account add column lock_reason varchar(40);
