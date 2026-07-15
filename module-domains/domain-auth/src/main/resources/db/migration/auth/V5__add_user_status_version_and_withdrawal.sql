-- 유저 상태 스냅샷 동기화용 단조 버전(순서 역전 방지·멱등 소비) + 탈퇴 시 로그인 이메일 파기 대비.
-- login_email을 null 허용으로 완화한다 — 탈퇴 계정은 이메일을 파기(null)하며, PostgreSQL 유니크
-- 인덱스는 null을 제외하므로 기존 uq_auth_account_login_email이 활성-only 유니크로 자연 좁혀진다.
alter table auth.auth_account
    add column user_status_version bigint not null default 0,
    alter column login_email drop not null;
