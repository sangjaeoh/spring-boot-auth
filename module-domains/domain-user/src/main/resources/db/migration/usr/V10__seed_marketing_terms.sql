-- 선택(마케팅) 약관 시드: 이용 중 동의/철회·수신설정 마케팅 동기화의 대상. 원문은 법무 확정 시 새 버전
-- 발행(publishVersion)으로 교체한다. UUID는 V5와 같은 이유로 고정 리터럴.
insert into usr.terms_document (id, type, required, created_at, updated_at) values
    ('01900000-0000-7000-8000-000000000004', 'MARKETING', false, now(), now());

insert into usr.terms_version (id, type, version, content, effective_from, created_at, updated_at) values
    ('01900000-0000-7000-8000-000000000014', 'MARKETING', 1, '마케팅 정보 수신 동의(선택) v1 (시드)', now(), now(), now());
