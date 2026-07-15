-- 로그인 시도의 지오로케이션 국가 코드(ISO 3166-1 alpha-2). 위험도 평가의 신규 지역 판정이
-- 최근 성공 이력의 국가 집합과 대조한다 — 저장 없이 매번 과거 IP를 재조회하면 왕복 비용이 들고
-- IP 90일 가명화 이후엔 위치 근거 자체가 소실된다.
alter table auth.login_attempt add column country_code varchar(2);
