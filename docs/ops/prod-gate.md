# Prod Gate

## 언제

- prod 배포 가부를 판정할 때(비협상 체크리스트).
- 외부 의존(조달·법무·조직) 항목의 오너·기한을 추적할 때.

## 판정 규칙

- 아래 코드 갭이 전부 "해소"이고, 외부 의존 게이트가 전부 완료 확인될 때만 prod 배포한다.
- 기한은 prod 배포 목표일(T) 역산이다 — 목표일 확정 시 절대 날짜로 치환해 추적한다.

## 코드 갭 실사 결과 (2026-07-16 — 전 항목 해소)

| 축 | 실사 당시 상태 | 해소 내용 | 검증 증거 |
| --- | --- | --- | --- |
| 시크릿 관리 | KEK·pepper가 main yml에 dev 정적 평문(3개 앱 동일 키) | `${CRYPTO_*}` 환경변수 주입 계약 + 미주입 fail-fast + dev 부트스트랩 스크립트([deployment](deployment.md)) | `CryptoSecretInjectionTest`, 앱 E2E가 prod 동일 변수명으로 부트 |
| 관측성·SLO | Actuator·메트릭·헬스 전무 | 분리 관리 포트 health 프로브 + Prometheus + 보안 탐지 카운터 + SLO·알람 기준([observability-slo](observability-slo.md)) | `ObservabilityE2EIT` |
| 부하·용량 | 하네스 전무 | 옵트인 프로파일 하네스 + 실측 리포트 + Argon2·Redis 용량 산정([load-report](load-report.md)) | 로컬 실측 전 항목 SLO 예산 내 |
| 컨테이너화·CD | Dockerfile·워크플로 전무 | 공용 Dockerfile(4앱) + migration init 스택 + 태그 릴리스 워크플로 + expand-contract 규약([deployment](deployment.md)) | 이미지 빌드 + 스택 기동 검증(migration exit 0 → health UP) |
| DR·HA | 문서 전무 | RTO/RPO·백업/PITR·Redis 지속성·키 사고 런북([dr-runbook](dr-runbook.md)) | 문서(복구 리허설은 인프라 확보 후 분기 1회) |
| 보안 테스트 | SCA·위협모델·패리티 테스트 전무 | Dependabot SCA + STRIDE 위협모델 + 열거 저항 패리티 테스트 + timing-safe 점검([threat-model](threat-model.md)) | `AuthApiE2EIT` 패리티, `PasswordApiE2EIT` 균일 응답 |
| OpenAPI 계약 | 툴링·스펙 전무 | springdoc 스펙 게시(/v3/api-docs, v1 계약 — presentation/v{n} 정합) | `OpenApiContractE2EIT` |
| 컴플라이언스 | 코드 밖(외부 의존) | 아래 외부 의존 게이트로 추적 | — |

## 외부 의존 게이트 (오너·기한·차단 대상)

| # | 항목 | 오너 | 기한 | 차단 대상 |
| --- | --- | --- | --- | --- |
| 1 | 법무 형식 최종검토 — 약관 원문·개인정보 처리방침 확정 | 법무·기획 | T-4주 | prod 배포 |
| 2 | PIA(개인정보 영향평가) 필요성 판정, 대상이면 수행 | 개인정보보호책임자(CPO) | 판정 T-8주 / 수행 T-2주 | prod 배포 |
| 3 | 펜테스트(외부 업체) + 지적사항 조치 | 보안 담당 | 수행 T-6주 / 조치 T-2주 | prod 배포 |
| 4 | 본인확인기관 계약(사이트코드) + NICE 실 페이로드 검증 | 사업·기획 | 실모드 전환 전(개발은 Mock 비블로킹) | `user.identity-verification.mode=nice` 전환 |
| 5 | 소셜 4사 개발자앱 등록·심사(애플 .p8 포함, code-exchange 배선) | 사업·기획 | 실모드 전환 전 | `auth.social.mode=oidc` 전환 |
| 6 | 알림 발송 벤더 계약(이메일·SMS·FCM) + 요청 페이로드 확정 | 사업·기획 | 실모드 전환 전 | `generic.notification.mode=vendor` 전환 |
| 7 | GeoIP 벤더 계약(신규 지역 로그인 실판정) | 사업·기획 | 실모드 전환 전 | GeoIP 실 어댑터 |
| 8 | prod 인프라 확보 — 시크릿 매니저(버저닝·복제), Redis Cluster(+AOF), PITR 백업, Prometheus 수집기·대시보드·알람 배선 | 운영 | T-4주 | prod 배포 |
| 9 | prod 유사 환경 부하 재실측(로컬 실측 보정) + 복구 리허설 1회 | 운영 | T-2주 | prod 배포 |
| 10 | 운영 최초 SUPER_ADMIN 시딩 런북 실행(user_role INSERT) | 운영 | 배포 직후 | 관리자 콘솔 사용 |

- 4~7은 prod 배포 자체를 막지 않는다(Mock 모드 배포 가능) — 해당 기능의 실사용 개시를 막는다. 단 본인인증(4)은 가입 플로우 전제라 대외 오픈 전 완료가 사실상 필수다.
- 코드 쪽 후속(비차단, 발견 시점 기록): 동일 계정 로그인 convoy 완화 검토([load-report](load-report.md)), KEK 전 행 재암호화 배치·pepper bidx 재산출 배치([dr-runbook](dr-runbook.md)), AuditLog 2년 후 crypto-shred 잡(todo 8번 메모), 휴면 사전통지 실 발송 배선(todo 7번 메모).
