-- 회원 생명주기 컬럼: 탈퇴·휴면 전이 시각 + 상태 스냅샷 동기화용 단조 버전 + 휴면 판정용 최근 로그인.
alter table usr.users
    add column status_version       bigint not null default 0,  -- 상태 전이마다 +1(인증 스냅샷 순서 역전 방지)
    add column last_login_at        timestamptz,                -- LastLoginObserved 소비로 갱신(휴면 판정)
    add column dormant_at           timestamptz,                -- DORMANT에서만 존재
    add column dormancy_notified_at timestamptz,                -- 휴면 30일 전 사전통지 발송 시각
    add column withdrawn_at         timestamptz;                -- WITHDRAWN에서만 존재(보존창 기산점)

-- 휴면 배치 스캔(상태 + 미접속 기간).
create index idx_users_status_last_login on usr.users (status, last_login_at);
