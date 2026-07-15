-- P1b 시드 최소셋: 가입 전제(필수) 약관 3종 + 각 v1. 약관 유형·버전 관리는 P4가 writer를 배선한다.
-- UUID는 고정 리터럴(반복 실행 없는 버전 마이그레이션이지만, 환경 간 동일 식별을 위해 결정적으로 둔다).
insert into usr.terms_document (id, type, required, created_at, updated_at) values
    ('01900000-0000-7000-8000-000000000001', 'SERVICE', true, now(), now()),
    ('01900000-0000-7000-8000-000000000002', 'PRIVACY_REQUIRED', true, now(), now()),
    ('01900000-0000-7000-8000-000000000003', 'AGE14', true, now(), now());

insert into usr.terms_version (id, type, version, content, effective_from, created_at, updated_at) values
    ('01900000-0000-7000-8000-000000000011', 'SERVICE', 1, '서비스 이용약관 v1 (시드)', now(), now(), now()),
    ('01900000-0000-7000-8000-000000000012', 'PRIVACY_REQUIRED', 1, '개인정보 수집·이용 동의(필수) v1 (시드)', now(), now(), now()),
    ('01900000-0000-7000-8000-000000000013', 'AGE14', 1, '만 14세 이상 확인 v1 (시드)', now(), now(), now());
