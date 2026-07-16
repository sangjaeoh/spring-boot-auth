# DR Runbook

## 언제

- 백업·복구 체계를 구성하거나 복구 리허설을 할 때.
- 키(KEK·pepper·서명키) 유실·유출 사고에 대응할 때.
- Redis·PostgreSQL 장애를 판정·복구할 때.

## RTO/RPO 목표

| 데이터 | 저장소 | RPO | RTO | 근거 |
| --- | --- | --- | --- | --- |
| 계정·PII·동의·감사·서명키(keyring) | PostgreSQL | ≤ 5분 | ≤ 60분 | WAL 아카이브 주기 / PITR 복원 리허설로 보정 |
| 세션·레이트리밋·챌린지 | Redis | 소거 허용 | ≤ 5분 | 재로그인으로 재구성 가능(아래 Redis 절) |

## 백업·PITR (PostgreSQL)

- 주기 베이스 백업 + WAL 아카이빙으로 PITR을 구성한다(매니지드 DB면 자동 백업 + PITR 옵션 활성). `keyring` 스키마(서명키)·`msg`(DLQ)까지 전 스키마가 한 인스턴스라 단일 백업 대상이다.
- 백업 유출 단독으로는 PII·서명키가 노출되지 않는다 — 봉투암호문이고 KEK는 시크릿 매니저에 분리 보관이다. 백업 접근 통제는 그래도 DB 본체와 동급으로 둔다(blind index는 pepper와 결합 시 역추론 표면).
- 복구 리허설을 분기 1회 실행한다: 최신 백업으로 스테이징 복원 → `app-migration` 기동(스키마 정합) → 로그인·조회 스모크. RTO 목표는 리허설 실측으로 보정한다.
- PII 컬럼 드롭 contract 마이그레이션 전에는 직전 백업 시점을 확인한다 — 드롭 후 복원해도 파기 정책상 원문 복구가 목적이 될 수 없다([deployment](deployment.md) expand-contract).

## Redis (세션 저장소)

- 세션은 소거성 데이터다 — 전면 유실 시 전 사용자 재로그인(가용성 이벤트)이고 보안 이벤트가 아니다. 무효화 이력·감사는 RDB가 진실원본이라 유실되지 않는다.
- prod는 Cluster + replica로 구성하고 revoke 내구 확인(`auth.session.revoke-durability.min-replicas≥1`)을 켠다 — failover 시 무효화 유실(로그아웃된 세션 부활)을 차단한다. AOF(everysec)를 켜 재시작 소실 폭을 줄인다.
- 장애 중 동작은 fail-closed다: 세션 검증 불가 → 401/503, 챌린지 발급 불가 → 503. 503 비율 알람([observability-slo](observability-slo.md))이 장애 신호다. 복구 후 별도 조치 없이 수렴한다.

## 키 사고 런북

- KEK(`CRYPTO_ENVELOPE_KEYS`) 유실 — 복호 불가 사고. 봉투암호 설계상 KEK 없이는 PII·서명키 원문이 존재하지 않는다.
  - 시크릿 매니저 버전 이력·복제본에서 복구를 시도한다. 복구 불가면 PII 전량 소실로 취급하고 재가입(본인인증 재수행) 플로우로 수습한다 — 이 시나리오를 만들지 않는 것이 통제 목표다: 시크릿 매니저 버저닝·다중 리전 복제·봉인 백업(오프라인)을 prod 전 구성한다([prod-gate](prod-gate.md)).
- KEK 유출 — 신규 버전 추가 + `CRYPTO_ENVELOPE_ACTIVE_VERSION` 상향(구 암호문은 동봉 버전으로 계속 복호). DB 접근까지 함께 뚫렸다고 판단되면 전 행 재암호화 배치(후속 구현 항목)와 서명키 회전을 병행한다.
- pepper(`CRYPTO_BLIND_INDEX_PEPPERS`) 유출 — blind index 역추론(이메일·전화 사전 대입) 표면이 열린다. 신규 버전 pepper 추가 + 전 행 bidx 재산출 배치가 필요하다(재산출 전까지 신규 쓰기만 새 버전). 유출 범위에 DB 덤프가 포함되면 개인정보 유출 신고 판정(법무)을 병행한다.
- 서명키(keyring) 유출 — `JwksRotationScheduler` 주기와 무관하게 즉시 회전을 실행하고(새 키 발급·게시), grace 창을 0으로 줄여 구키 검증을 끊은 뒤 전 세션 강제 로그아웃으로 발급 토큰을 전멸한다(세션이 진실원본이라 Access 폐기가 실시간 반영된다).

## 다중 인스턴스·재기동

- 앱은 무상태다 — 인스턴스 손실은 LB에서 제외되면 끝이고, 서명키는 공유 keyring이라 재기동 후에도 직전 발급 토큰이 검증된다(9번 슬라이스에서 확보). 특별한 복구 절차가 없다.
