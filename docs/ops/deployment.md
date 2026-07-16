# Deployment

## 언제

- 앱을 컨테이너로 빌드·배포하거나 배포 파이프라인을 구성할 때.
- prod 환경변수(시크릿) 주입 계약을 확인할 때.
- 스키마 마이그레이션을 배포와 함께 굴릴 때(expand-contract).

## 시크릿 주입 계약

- 시크릿 원문은 레포·이미지에 두지 않는다. 시크릿 매니저(벤더 무관 — AWS Secrets Manager·Vault 등)가 컨테이너 환경변수로 주입한다.
  - 미주입 기동은 placeholder 미해결로 fail-fast한다(계약 테스트: `CryptoSecretInjectionTest`).
- 주입 변수 목록. KEK와 pepper는 분리 보관한다(pepper 유출 시 blind index 역추론 가능).

  | 변수 | 값 형식 | 소비처 |
  | --- | --- | --- |
  | `CRYPTO_ENVELOPE_KEYS` | `version:base64` CSV (AES-256, 32바이트) | PII 봉투암호 KEK + JWT 서명키 keyring 봉투암호(`JdbcSigningKeyStore`) — app-api·app-admin·app-batch |
  | `CRYPTO_ENVELOPE_ACTIVE_VERSION` | 정수(기본 1) | KEK 회전 시 상향 |
  | `CRYPTO_BLIND_INDEX_PEPPERS` | `version:base64` CSV (HMAC-SHA256, ≥32바이트) | 이메일·전화 blind index pepper — app-api·app-admin·app-batch |
  | `CRYPTO_BLIND_INDEX_ACTIVE_VERSION` | 정수(기본 1) | pepper 회전 시 상향 |
  | `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | JDBC URL·계정 | 전 앱(yml의 localhost·auth/auth 기본값은 로컬 compose 전용) |
  | `SPRING_DATA_REDIS_*` (host·port·cluster.nodes 등) | Redis 접속 | app-api·app-admin·app-batch(prod Cluster 오버레이 소유) |
- 조달 후 실모드 전환 시 추가 주입(미주입 기동은 각 어댑터가 fail-fast): `generic.notification.mode=vendor`의 `generic.notification.{email|sms|push}.*`, `user.identity-verification.mode=nice`의 `user.identity-verification.nice.*`, `auth.social.mode=oidc`의 `auth.social.oidc.{provider}.client-id`, 애플 code-exchange 배선 시 `.p8` 개인키(조달 항목 — [prod-gate](prod-gate.md)).
- 로컬 개발은 `scripts/generate-dev-secrets.sh`로 `.dev-secrets.env`(gitignored)를 만들고 `set -a; source .dev-secrets.env; set +a` 후 기동한다.
  - `.dev-secrets.env` 재생성 시 기존 로컬 DB 암호문(PII·keyring)은 복호 불가 — DB 볼륨도 함께 초기화한다.
- 테스트는 각 앱 `src/test/resources/application.properties`가 prod와 동일한 변수명으로 테스트 픽스처 키를 주입한다(주입 경로 검증 겸용). 테스트 키는 dev·prod 어디에도 쓰지 않는다.
- pepper·KEK 유출 대응은 [dr-runbook](dr-runbook.md)이 소유한다.
