-- 동의 이력에 마케팅 채널 한정 선택 컬럼 추가(MARKETING 동의에만 사용, 그 외 null).
alter table usr.consent_record add column channel varchar(10);
