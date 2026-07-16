# Deployment

## 언제

- 앱을 컨테이너로 빌드·배포하거나 배포 파이프라인을 구성할 때.
- prod 환경변수(시크릿) 주입 계약을 확인할 때.
- 스키마 마이그레이션을 배포와 함께 굴릴 때(expand-contract).

## 컨테이너·롤아웃

- 이미지는 루트 `Dockerfile` 하나가 4개 앱을 빌드한다(`--build-arg APP=app-{name}`). 빌드는 컨테이너 안 래퍼 빌드로 재현 가능하고, 테스트는 CI 게이트가 소유한다(이미지 빌드는 bootJar만).
- 게시는 `.github/workflows/release-images.yml`이 버전 태그(v*)에서 GHCR로 푸시한다. 태그는 품질 게이트 통과 커밋에만 붙인다.
- 롤아웃 순서: `app-migration`을 init 컨테이너(또는 선행 잡)로 실행해 성공 종료를 확인한 뒤 앱을 교체한다. 로컬 재현은 `docker-compose.yml`(`service_completed_successfully` 배선).
- 프로브: liveness `/actuator/health/liveness`, readiness `/actuator/health/readiness`(관리 포트 — [observability-slo](observability-slo.md)). 관리 포트는 외부에 게시하지 않는다.
- 앱은 무상태(세션·잠금 카운터는 공유 Redis, 서명키는 공유 `keyring` 스키마)라 인스턴스 수평 확장·구버전 병행이 안전하다 — 카나리는 트래픽 일부를 신버전 인스턴스로 보내고 [observability-slo](observability-slo.md)의 알람 기준(5xx·p99·실패율)으로 승격/롤백을 판정한다.
- Mock↔실 어댑터 피처플래그(`auth.social.mode`·`user.identity-verification.mode`·`generic.notification.mode`)는 인스턴스 설정 단위로 적용되므로 카나리 인스턴스에만 실모드를 켜 점진 전환할 수 있다(미주입 기동 fail-fast).
- Redis는 prod에서 Cluster 오버레이(`spring.data.redis.cluster.nodes`)와 revoke 내구 확인(`auth.session.revoke-durability.min-replicas≥1`)을 설정한다(정본: application.yml 주석·[dr-runbook](dr-runbook.md)).

## 마이그레이션 규약(expand-contract)

- 스키마 변경은 expand와 contract를 다른 릴리스로 분리한다. 롤아웃 중 구·신 코드가 병행하므로 한 릴리스의 마이그레이션은 구 코드와 호환되어야 한다.
  - expand(같은 릴리스 허용): 테이블·컬럼·인덱스 추가, 널러블 컬럼, 트리거·이중쓰기 도입.
  - contract(구 코드 완전 퇴역 후 별도 릴리스): 컬럼·테이블 드롭, NOT NULL 강화, 제약 추가.
- 적용된 마이그레이션 파일은 수정하지 않고 새 버전을 추가한다(1변경 1파일 — 정본: [architecture](../architecture.md) 도메인 모듈 구조).
- PII 컬럼·테이블 드롭은 롤백 불가로 취급한다 — 즉시 파기·crypto-shred 정책상 백업 복원으로도 원문이 돌아오지 않는다(봉투암호 원문은 키 없이 무의미). 드롭 contract는 보존기간 검증과 [dr-runbook](dr-runbook.md)의 백업 시점 확인을 선행한다.
- 마이그레이션 실패 시 롤아웃을 중단한다(앱은 이전 버전 유지 — init 실패로 신버전이 뜨지 않는 게 정상 동작이다).

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
