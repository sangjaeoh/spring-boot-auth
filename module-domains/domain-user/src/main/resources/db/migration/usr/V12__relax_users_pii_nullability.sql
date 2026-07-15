-- 탈퇴 시 PII 즉시 파기(개보법 §21): 파기된 행은 PII 컬럼을 null로 소거하므로 not null을 해제한다.
-- ci_hash도 파기 대상 — 탈퇴 후 해시 보존은 CiRegistry tombstone이 단독 소유한다(분리보관·유한 6개월).
alter table usr.users
    alter column name drop not null,
    alter column birth_date drop not null,
    alter column gender drop not null,
    alter column contact_email drop not null,
    alter column carrier drop not null,
    alter column contact_phone drop not null,
    alter column contact_phone_bidx drop not null,
    alter column ci_hash drop not null;
