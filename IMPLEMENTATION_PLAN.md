# 구현 계획 (Implementation Plan)

이 문서는 `REQUIREMENTS.md`(무엇을)와 `DOMAIN_MODEL.md`(무엇을 만드는가, 필드 수준)를 `docs/`의 네 규칙 문서(어떻게)에 실어 **백엔드를 어떤 순서로 빌드하는가**를 소유한다. 규칙·필드·정책은 재서술하지 않고 참조만 한다.

- 요구사항: [`REQUIREMENTS.md`](REQUIREMENTS.md) · 도메인 모델: [`DOMAIN_MODEL.md`](DOMAIN_MODEL.md)
- 규칙: [`docs/architecture.md`](docs/architecture.md) · [`docs/coding-conventions.md`](docs/coding-conventions.md) · [`docs/entity-persistence.md`](docs/entity-persistence.md) · [`docs/code-quality.md`](docs/code-quality.md)

이 개정본은 두 건의 독립 CTO 리뷰(총평 YELLOW)를 반영해, 타당하다고 판단한 지적만 수용했다. 핵심 변경: 기능을 요구사항 Phase 1 최소 범위(핫패스→최소 온보딩)로 축소, 온보딩을 단일 트랜잭션으로 원자화해 사가·고아 문제 제거, build-vs-buy·저장소 토폴로지·개략 공수·운영/보안 게이트를 결정으로 표면화.

## 레포 현재 상태

- 규칙·요구·설계 문서만 존재한다. 코드·Gradle·빌드 하네스·강제 장치(컨벤션 플러그인·아키텍처 테스트)는 아직 없다.
- README는 "골격이 이미 구성된 저장소 위의 개발"을 전제하나, 이 레포엔 그 골격이 없다 → **0단계에서 골격·하네스를 세운다**.
- 강제 장치가 배선되기 전까지 유일한 게이트는 `docs/architecture.md`의 "빌드가 강제하는 불변식" 목록에 대한 자기검증이다(AGENTS.md 앵커).

## 0. 전제와 확인 필요 결정

문서로 확정하지 못한 지점을 조용히 고르지 않고 명시한다. 다르면 되돌린다.

| # | 결정 | 채택값(진행) | 근거 | 확신 |
|---|---|---|---|---|
| D1 | RDB 제품 | **PostgreSQL 17** | `docs/`(규칙 소유)·README가 PostgreSQL 명시. code-quality는 H2 기각·Testcontainers PostgreSQL로 근거화. `DOMAIN_MODEL`은 RDB 벤더를 소유하지 않으므로 벤더 표기 제거해 정합화 | 높음 — 진행 |
| D2 | Phase 1 범위 | **핫패스(P1a) 먼저 → 최소 로컬 온보딩(P1b)**. 본인인증(실)·약관관리·동의철회·탈퇴·휴면·CI 쿨다운은 Phase 4 | 제품 명제(Redis 세션 + O(1) 실시간 무효화)는 온보딩과 무관하므로 가장 어려운 사가를 MVP 최우선으로 두지 않는다. `User` 불변식(본인인증∧필수동의)은 P1b가 Mock 본인인증 + 시드 최소약관으로 충족. 요구사항 Phase 1에 정확히 대응 | 높음 — 진행 |
| D3 | 이벤트 전달 | 통합 이벤트 **스키마 공개·안정 고정 + 멱등 소비자 + 내구 재시도(DLQ) 테이블**. `@TransactionalEventListener(AFTER_COMMIT)`로 발행. **아웃박스 릴레이는 실 브로커/물리 분리 시 도입** | 단일 프로세스 모놀리식에서 아웃박스 릴레이가 막는 실패(브로커 다운 시 발행 유실)는 분산 전엔 실재하지 않고 in-process로 검증도 안 된다(AGENTS #2). 스키마·멱등·DLQ는 값싸고 교체 비용을 낮춘다 | 높음 — 진행 |
| D4 | 도메인 모듈 분할 | 스키마 기준 **3개**(`domain-user`/`domain-auth`/`domain-generic`) | "도메인마다 스키마 하나" + seam 3개. 비대해지면 내부 수정 없이 재분할 | 중간 — 진행 |
| D5 | 배포 토폴로지 | 단일 실행 앱(`app-api`)에 도메인 조립, 이벤트로 결합 | 모놀리식이되 seam 계약으로 결합해 물리 분리가 기계적 | 중간 — 진행 |
| D6 | 저장소 토폴로지 | **단일 PostgreSQL 인스턴스 + 스키마 3개 + 크로스스키마 조인/FK 금지(빌드 강제)**. 온보딩·탈퇴 등 크로스스키마 정합은 **단일 ACID 트랜잭션**으로 원자화 | 한 인스턴스 안 무조인 스키마 분리가 "DB 미공유(논리 분리)"를 충족하고 물리 분리성도 보존한다. 단일 트랜잭션이 가능하므로 고아-User·전진복구·재조정 스윕이 불필요 — 분산 문제를 실재 전에 만들지 않는다. 별도 인스턴스를 강제하면 사가·복구 원장이 부활(§6) | 확정 (사용자 승인) |
| D7 | build vs buy | 인증 **seam·한국 특화(본인인증·PIPA 동의/보존·CI 중복방지·카카오/네이버)는 자체 구축**. 커모디티 원자재(JWT/JWKS·OIDC 클라이언트·비밀번호 KDF·OAuth2 서버 기능)는 **검증 라이브러리에 위탁**(Spring Security·Spring Authorization Server·Nimbus JOSE·표준 Argon2) | 토큰/세션/소셜/RBAC를 0에서 손으로 짓는 것은 정당화되지 않는다. 라이브러리에 실어 재발명(특히 애플 동적 client_secret·id_token 검증)을 피한다. 상용 IdP 전면 채택은 stateful Redis 세션·CI dedup·PIPA 흐름 부적합으로 기각 | 중간 — 진행 |

- D1·D2·D6은 확정이다(D6은 사용자 승인 — 단일 PostgreSQL, 스키마 분리). 이로써 온보딩·탈퇴는 단일 트랜잭션으로 원자화되고 사가·전진복구·고아 처리가 불필요하다. 물리 별도 DB로 전환하는 시점이 곧 사가를 재도입하는 물리 분리 지점이다.

## 0.1 개략 공수·비용 (커밋 아님)

숙련 2~4인 팀 가정의 거친 레인지다. 착수 후 스파이크로 정밀화한다.

| 구간 | 개략 공수 | 비고 |
|---|---|---|
| Phase 0 | 2~4주(도구 반항 시 최대 8주) | 타임박스 필수. 사전 도구 호환성 스파이크(아래) |
| Phase 1 (P1a+P1b) | 5~8주 | 세션 핫패스·JWKS·PII crypto·최소 온보딩 |
| Phase 2~3 | 4~7주 | 소셜 4종·디바이스·다중세션 |
| Phase 4~6 | 3~5개월 | 본인인증 실·약관/동의·탈퇴/보존·알림·관리자·감사 |
| 전체 | 약 6~12개월 | 2~4인 프로그램. 아래 비용 드라이버 별도 |

- 비용 드라이버(운영비, 계획에 포함해 추적): 본인인증 건당 과금(NICE/KG/다날), 국내 SMS 건당 과금, Redis HA 메모리, KMS 호출(envelope 암호화로 완화), PostgreSQL 운영. 규모 시 유의미 — 비용 모델을 별도 산출한다.

## 1. 목표 모듈 지도 (이 프로젝트)

`docs/architecture.md`의 일반 5계층을 이 프로젝트의 3서비스·17애그리거트로 구체화한다. 각 모듈은 계층 컨벤션 플러그인 하나만 적용한다.

| 계층 | 모듈 | 담는 것 | 등장 단계 |
|---|---|---|---|
| build-logic | 컨벤션 플러그인 | `java-base`·`java-common` + 계층별(`domain`/`app`/`infra`/`external`/`common`)-module | P0 |
| common | `common-core` | `UuidV7Generator`·시간·`BaseException`·`ErrorCode`·crypto 원자재(AES-GCM/HMAC 인터페이스·envelope) | P0 |
| common | `common-jpa` | `BaseTimeEntity`·Auditing·`SchemaFlywayFactory`·PII 컬럼 컨버터(키버전·blind index 지원) | P0 |
| common | `common-messaging` | `MessagePublisher` 포트·멱등 소비 지원·내구 재시도(DLQ) 지원 | P1 |
| common | `common-auth` | JWT 발급/검증·JWKS·클레임(`sid`·roles). **Nimbus JOSE / Spring Authorization Server 위에** | P1 |
| common | `common-web` | 인증 필터(JWT 검증 + 세션 O(1) 검증)·멱등 필터·레이트리밋 필터·`AuthUser`·ProblemDetail·CORS/CSRF. **Spring Security 위에** | P1 |
| domain | `domain-user`(`usr`) | User·IdentityVerification·CiRegistry·Terms·Consent·NotificationPreference·RBAC. 포트 `IdentityVerificationProvider` | P1b→확장 |
| domain | `domain-auth`(`auth`) | AuthAccount·PasswordCredential·SocialConnection·Device·LoginAttempt(RDB) / AccountSessions·VerificationChallenge·RegistrationSession(Redis) + 정책. 포트 `SocialIdentityProvider`·`GeoIpLookup`·`NotificationSender` | P1→확장 |
| domain | `domain-generic`(`generic`) | Notification·AuditLog. 포트 `NotificationSender`(EMAIL/SMS/PUSH) | P5 |
| infra | `infra-redis` | 세션 원자연산(Lua)·코드 TTL·온보딩 상태·잠금/레이트 카운터. Redis 애그리거트 영속 포트 구현 | P1 |
| infra | `infra-messaging` | `MessagePublisher` transport(초기 in-process) + DLQ. 릴레이는 브로커 도입 시 | P1 |
| infra | `infra-crypto` | KMS 포트 구현(dev 정적 키)·데이터키 캐싱(envelope)·pepper 공급 | P1 |
| external | `external-identity` | `IdentityVerificationProvider`(NICE/KG/DANAL) + **Mock** | P1b(Mock)→P4(실) |
| external | `external-social` | `SocialIdentityProvider`(Kakao/Naver/Google/Apple OIDC, 애플 `.p8`) + Mock | P2 |
| external | `external-notification` | `NotificationSender`(SMTP/SES·SMS·FCM/APNs) + Mock | P1(Mock)→P5(실) |
| app | `app-api` | 공개 API·파사드·크로스도메인 리스너 | P1 |
| app | `app-migration` | Flyway 스키마별 독립 실행 | P0 |
| app | `app-admin` | 관리자 콘솔(`infrastructure/query` 크로스스키마 read) | P6 |
| app | `app-batch` | 휴면 전환·보존 파기·스윕 잡 | P4→P5 |

- Redis 애그리거트(세션·코드·온보딩)는 "Spring Data가 곧 포트"가 성립하지 않는 유일 지점 → 도메인이 영속 포트를 선언하고 `infra-redis`가 Lua 원자성으로 구현한다.

## 2. 구현 전략 (원칙)

- **수직 슬라이스**: 애그리거트 단위로 마이그레이션→엔티티→리포지토리→서비스→경계→API를 한 번에 끝낸다. 계층별 가로 빌드를 하지 않는다.
- **리스크 우선 순서**: 가장 어렵고 제품 가치가 큰 표면(세션 실시간 무효화 핫패스)을 먼저 굳히고, 통합 복잡도(온보딩)는 그 뒤에 얹는다.
- **검증 라이브러리 위에 선다(D7)**: JWT/JWKS·OIDC 클라이언트·비밀번호 KDF·OAuth2 서버 기능은 자작하지 않는다. seam·도메인 규율·한국 특화만 우리 코드로 짓는다.
- **Mock-first 외부연동**: 본인인증·소셜·알림·GeoIP는 포트로 격리하고 dev/test는 Mock로 오프라인 E2E를 성립시킨다. Mock↔실 어댑터는 피처플래그로 스위치한다.
- **이벤트로 seam 결합(D3)**: 크로스 도메인 조율은 커밋 후 이벤트로 최종일관성을 취하되, 크로스스키마 정합이 필요한 원자 조작은 단일 트랜잭션으로 처리한다(D6). 한 트랜잭션은 한 애그리거트만 바꾼다 — 단, 온보딩·탈퇴처럼 seam 계약이 명시한 원자 조작은 예외로 단일 트랜잭션에 묶고 그 사실을 명시한다.
- **테스트 전략**: 불변식·거부 케이스는 단위, 영속·유니크·인덱스는 Testcontainers(PostgreSQL)·Redis 통합, 플로우는 E2E happy+거부. 버그 수정은 재현 테스트 선작성(AGENTS #4).
- **자기검증 루프**: 각 슬라이스 종료 시 "빌드가 강제하는 불변식"으로 대조하고 `./gradlew build`를 green으로. 강제 장치가 배선된 항목은 의도적 위반이 빌드를 깨는지 스팟체크.
- **외과적 변경**: 바뀐 모든 줄이 해당 단계 목표로 추적돼야 한다. 투기적 유연성·미요청 추상화를 넣지 않는다(AGENTS #2·#3).

## 3. 애그리거트 구현 표준 루프

애그리거트 1개를 끝내는 반복 절차다. 이후 각 단계는 "이 루프를 이 애그리거트들에 적용 + 플로우 배선"으로 기술한다.

```
1. Flyway 마이그레이션(스키마·테이블·유니크·논리 FK 인덱스·필요시 CHECK·blind index) → 검증: app-migration 기동 + ddl-auto=validate 통과
2. 엔티티·VO·AttributeConverter·상태 enum·의도 동사 메서드·create() 팩토리         → 검증: Testcontainers 저장/조회·전이 가드 테스트
3. repository(파생 → @Query → QueryDSL 최소 단계)                              → 검증: 쿼리·유니크 위반 테스트
4. service(Reader/Appender/Modifier/Processor/Validator) + @Transactional       → 검증: 불변식·거부 케이스 단위 테스트
5. info record(경계 변환) + 도메인/통합 이벤트 record                          → 검증: 경계 밖 엔티티 미노출(아키텍처 테스트)
6. (앱) controller + request/response DTO + facade 조율 + event/listener        → 검증: E2E happy + 거부
7. 자기검증: "빌드가 강제하는 불변식" 대조                                     → 검증: ./gradlew build green + 위반 스팟체크
```

## 4. 단계별 구현 계획

구현 단계는 릴리스 Phase(REQUIREMENTS)를 빌드 가능성·리스크 기준으로 재편했다.

### Phase 0 — 빌드 하네스 + 골격 (전제, 타임박스)

목표: `./gradlew build`가 도는 멀티모듈 골격과 강제 장치를 세운다. **상한 2주(도구 반항 시 재협상), walking skeleton 우선.**

0. **사전 도구 호환성 스파이크(2일)**: Java 25 + Gradle 9.5 + Spring Boot 4.1 + NullAway 0.13 + Error Prone 2.50이 실제로 함께 도는지 최소 모듈로 검증 → 검증: 스파이크 모듈 컴파일·정적분석 green. 반항 시 버전·폴백을 P0 착수 전 확정
1. Gradle wrapper·`settings.gradle.kts`·`gradle/libs.versions.toml`(버전 정본) → 검증: `./gradlew help`
2. **walking skeleton 먼저**: 사소한 엔티티 1개로 마이그레이션→엔티티→리포지토리 E2E 파이프라인 통과 → 검증: `ddl-auto=validate` 통과 + Testcontainers 저장/조회
3. `convention.java-base`(Spotless·NullAway·Error Prone)·`java-common`(Lombok·H2 차단) → 검증: 위반 코드가 컴파일 실패
4. 계층 플러그인 + 아키텍처 테스트(엔티티 미노출·리포지토리 직접접근 금지·`@NullMarked`·base finder 금지)를 **증분 배선** → 검증: 표본 위반이 실패
5. `common-core`·`common-jpa` 확정 + `app-migration` + 로컬 PostgreSQL/Redis `docker-compose` → 검증: 빈 스키마 마이그레이션 기동
6. **병렬 착수(비-코딩, 지금 시작)**: 본인인증기관·소셜 각 사 계약/심사·Apple Developer 등록·법무(PIPA·PIA 필요성) 조달 워크스트림 개시 → 검증: 오너·리드타임·마일스톤 기록

산출물: 강제 게이트 + 외부 조달 시계. 강제 장치가 배선된 항목부터 자기검증이 빌드로 강제된다.

### Phase 1a — 인증 핫패스 MVP (제품 명제 검증)

목표: 실시간 세션 무효화라는 제품 명제를 가장 싸게 검증한다. 회원은 정식 생성 진입점 `CreateUser`(P1b가 온보딩으로 감쌀 그 커맨드)를 부트스트랩/픽스처 경로로 호출해 시드한다(버리는 해킹이 아니라 실제 생성 경로 재사용).

대상: `AuthAccount`·`PasswordCredential`·`AccountSessions`/`Session`(Redis)·`LoginAttempt` + `common-auth`·`common-web`·`infra-redis`·`infra-crypto`.

1. 로그인 + 세션 발급: 접근 판정(로컬 스냅샷 `userStatus==ACTIVE ∧ lockState==NONE`)→비번 검증→세션 생성(Access `sid` + Refresh) → 검증: E2E 로그인 + `LoginAttempt` 기록
2. 매 요청 세션 O(1) 검증·로그아웃(`revoke`) → 검증: 로그아웃 후 기존 Access 즉시 401
3. Refresh 회전 + 유예 창 + 재사용(탈취) 감지→`revokeAll` → 검증: 정상 재시도 허용 / 유예 밖 재사용이 패밀리 무효화
4. 비밀번호 정책·최근 N 재사용 금지·재설정(토큰 해시·TTL) → 검증: 정책 위반·재사용·만료 토큰 거부
5. **Argon2id를 바운드 실행기로 격리**(가상 스레드 CPU-바운드 피닝·메모리 폭주 방지) + 동시성 상한을 용량 모델에 반영 → 검증: 로그인 폭주 부하에서 메모리·지연 안정
6. **선행 게이트(§5)**: Redis HA·failover 무효화 정합·Lua keyslotting, PII 키버전·pepper버전·전화 blind index 포맷을 이 단계 전에 확정 → 검증: 설계 결정 문서화 + 페일 모드 테스트

### Phase 1b — 최소 로컬 온보딩 (가입, 요구사항 Phase 1 완성)

목표: 불변식을 지키는 최소 실제 가입. 외부연동은 Mock, 약관은 시드 최소셋.

대상: `RegistrationSession`(Redis)·`VerificationChallenge`(Redis)·최소 `Terms`/`Consent`(시드)·`IdentityVerification`(Mock)·`CiRegistry`(link만)·`User`.

1. 로컬 온보딩: 가입 시작→이메일/휴대폰 코드 검증→Mock 본인인증(`IdentityVerified`, ciHash)→필수동의 버퍼 → 검증: 스텝 미충족 거부
2. **완료를 단일 크로스스키마 트랜잭션으로 원자화(D6)**: `CreateUser`가 UserId 채번→`User(ACTIVE)`→`ConsentRecord` append→`CiRegistry.link`(유니크 hard-enforce)→`IdentityVerification` 연결→`AuthAccount`+`PasswordCredential` 생성을 **한 트랜잭션**에 → 검증: 중간 실패 시 전량 롤백(고아-User 불가), CI 중복 거부
3. PII 암호화 컬럼·`ciHash` 해시·전화 blind index 저장 → 검증: 평문 PII 컬럼 부재 + 전화 동등조회 가능
4. 영속 PENDING 금지(온보딩 상태는 Redis TTL, 정식 회원은 트랜잭션 성공 시에만) → 검증: 좀비 계정 미생성

경계 메모: 단일 트랜잭션이 사가·전진복구·재조정 스윕·고아 처리를 제거한다. 통합 이벤트 스키마는 공개로 고정해 물리 분리 시 사가를 기계적으로 재도입한다. 실 본인인증·약관 버전관리·동의 철회·CI 쿨다운·탈퇴·휴면은 P4.

### Phase 2 — 소셜 로그인 4종 + 연동/해제

대상: `SocialConnection` + `external-social`(OIDC, 라이브러리 기반) + 소셜 온보딩(단일 트랜잭션 재사용).

1. `id_token` 검증·`(provider, subject)` 유니크·소셜 최초 로그인 온보딩(SOCIAL 스텝셋) → 검증: 신규 소셜 E2E 가입
2. 연동/해제 + `LoginMethodPolicy`(≥1 수단 유지) → 검증: 마지막 수단 해제 거부. **동시 해제 TOCTOU(둘 다 "하나 남음" 관측→0) 방지: `AuthAccount` 행 잠금/카운트 제약으로 직렬화** → 검증: 동시 해제 2건 중 하나만 성공
3. 애플 특수(동적 `client_secret`·`id_token`·릴레이 이메일 `isPrivateRelay`) → 검증: 릴레이 이메일이 `contactEmail` seed
4. dev Mock/샌드박스 → 검증: 오프라인 E2E

### Phase 3 — 디바이스 + 다중 로그인 제한 + 강제/원격 로그아웃

대상: `Device` + 세션 정책 확장.

1. 기기 인식(세션 생성 전 지문 매칭)·등록·목록·신뢰/해제·푸시 토큰/권한 → 검증: 신규 기기 인식 + `NewDeviceDetected`
2. 동시 활성 세션 ≤ N 원자 검증 + 초과 시 최오래 축출(`ConcurrentLimitExceeded`) → 검증: N+1 로그인 시 최오래 종료
3. 내 세션 목록·특정/현재제외 전체 원격 로그아웃 → 검증: 원격 종료 즉시 반영
4. 기기 삭제→해당 기기 세션 종료(`revokeByDevice`) → 검증: 삭제 후 그 기기 Access 401
5. 관리자 강제 종료 훅(`ForceLogoutRequested` 소비, 콘솔 UI는 P6) → 검증: 이벤트 소비 시 `revokeAll`

### Phase 4 — 본인인증(실) + 약관·동의 + 탈퇴/보존 + 휴면

P1b의 최소 슬라이스를 정식 기능으로 확장한다.

1. `external-identity` 실 어댑터 배선(포트 계약 유지) → 검증: Mock↔실 스위치가 도메인 무변경
2. 약관 유형·버전 관리·재동의 유발(`ReConsentRequired`)·이용 게이트(`ConsentPolicy`) → 검증: 필수 새 버전 발행 시 재동의 요구
3. 동의/철회(선택만)·`ConsentState` fold·마케팅↔`NotificationPreference` 동기화 → 검증: 필수 철회 거부 + 마케팅 동기화
4. 탈퇴(`withdraw`→WITHDRAWN·PII 즉시 파기·CI tombstone) + **신규 로그인 창 닫기: 탈퇴가 인증-로컬 status를 동기 플립하거나 로그인/세션생성이 pending-withdrawal에 fail-closed**(파기된 정체성에 fresh 세션 발급 방지) → 검증: 탈퇴 직후 신규 로그인 거부 + 기존 세션 전멸
5. 재가입 쿨다운(tombstone `withdrawnAt + cooldown`) → 검증: 쿨다운 내 거부·잔여일 반환
6. 휴면 전환(12개월·30일 전 통지)·해제는 **유저 서비스 권위의 OTP 재인증으로 강제**(스냅샷이 ACTIVE여도 통과 금지 — 안전 비대칭은 WITHDRAWN 전용) → 검증: 스냅샷 지연 상황에서도 DORMANT는 OTP 없이 로그인 불가
7. `app-batch`: 휴면 전환·보존 파기(`RetentionPolicy`, append-only는 PII만 crypto-shred/가명화) → 검증: 경계값 전환·파기 잡 동작

### Phase 5 — 이상탐지·알림 + 레이트리밋·계정 잠금

대상: `domain-generic`(Notification·AuditLog 일부)·`external-notification`(실)·`GeoIpLookup`.

1. 신규 기기/신규 지역 감지·`RiskEvaluator`(riskScore) → 검증: 신규 지역 로그인이 riskScore 상승
2. 발송 판정(`NotificationDispatchPolicy` = 유저 수신설정 AND 기기 푸시권한 AND 카테고리)·수신자=유저 contact → 검증: opt-out 반영 + 채널 AND
3. 보안 카테고리 강제 발송(opt-out 무시) → 검증: 보안 알림이 수신거부에도 발송
4. 알림 수신 설정(채널×카테고리 opt-in) API → 검증: SECURITY 최소 채널 해제 거부
5. 레이트리밋(민감 엔드포인트 IP/계정 + 코드 일일 한도) → 검증: 임계 초과 시 429
6. 연속 실패 잠금(Redis 카운터+TTL→`lock(TEMP)`)·쿨다운 자동 해제 → 검증: N회 실패 후 `TEMP_LOCKED`, 쿨다운 후 해제
7. 발송 실패 재시도·격리(DLQ) → 검증: Mock 실패 주입 시 재시도

### Phase 6 — 관리자 콘솔 + RBAC + 감사

대상: `app-admin`·RBAC·`AuditLog` 정식화.

1. RBAC 배정(유저)·집행(인증, 토큰 클레임)·`RoleChanged`→클레임 갱신 → 검증: 관리자 API 접근통제
2. 관리자 콘솔: 회원 검색(전화 blind index 활용)/상세(마스킹)·강제 로그아웃·잠금/해제·이력 조회·권한 변경 → 검증: 마스킹 + 강제 로그아웃 즉시 반영
3. `infrastructure/query` 격리 구역 크로스스키마 read → 검증: 읽기 전용, 경계 무침범
4. 감사 로그: append-only·전/후 값·수정 불가(WORM)·조회 → 검증: update/delete 차단 + 전/후 기록
5. 실효 회원상태 읽기모델(생명주기+잠금 합성) → 검증: 조합 상태 렌더

### Phase 7 — MFA (옵션, 슬롯만)

- 본 범위 밖. `AuthenticationFactor` 형제 `TotpCredential`·`ChallengeType.MFA`·스텝업 훅·예약 이벤트 자리만 유지한다.

## 5. 횡단 관심사 (오너 있는 워크스트림)

한 줄 언급이 아니라 단계 배정·오너·검증을 갖는 1급 항목이다.

- **PII 암호화·조회(P1 확정)**: AES-GCM **envelope 암호화 + 데이터키 캐싱**(KMS-per-op 비용/지연 회피), **암호문에 키 버전(key id)** 동봉(회전 시 전행 재암호화 회피), `ciHash`·전화 등 조회 필요 PII는 **blind index(HMAC+pepper) + pepper 버전 스킴**(pepper 회전이 유일성/조회를 깨지 않게). 대상 컬럼은 `DOMAIN_MODEL` 영속 유의절이 소유.
- **Redis HA(P1a에서 확정 — [`REDIS_HA.md`](REDIS_HA.md))**: 세션=요청 100% 핫패스이자 진실원본. 토폴로지=Cluster, AOF 지속성, **Lua keyslotting(단일 슬롯 hash tag)은 테스트가 강제**, **failover revoke 유실 방지**(min-replicas로 창 축소, 완전 차단은 WAIT 후속), 다운 시 fail-closed(읽기=검증 거부·쓰기=503) 정책을 결정문서 + 운영설정으로 명문화·코드로 강제.
- **이벤트(D3)**: 통합 이벤트 스키마 공개·안정 + 멱등 소비자 + 내구 재시도(DLQ). 아웃박스 릴레이는 브로커/물리 분리 시.
- **보존·파기**: `RetentionPolicy` 외부화·데이터 소유=파기 소유. append-only 3종은 PII만 crypto-shred/가명화. P4 batch 집행.
- **관측성·SLO**: 로그인 p99·가용성 SLO, seam 넘는 분산 트레이싱, **보안 탐지 지표**(재사용 감지 급증·잠금율·회전 이상) 대시보드·알럿. P1부터 증분.
- **성능·용량**: 세션 핫패스·Lua 경합 부하 테스트, Redis 메모리 모델(세션당×동시×상한), Argon2id 비용 예산. P1a에 착수.
- **CI/CD·롤아웃**: 파이프라인·테스트 게이트·이미지 빌드·마이그레이션 실행(app-migration init 컨테이너). Flyway **expand-contract**(PII 컬럼 드롭은 롤백 불가), Mock↔실 어댑터 피처플래그·카나리. P0 후 배선.
- **시크릿 관리**: JWT 서명키·DB·소셜 시크릿·애플 `.p8`·기관 사이트코드·**ciHash pepper**의 저장/주입/회전. pepper 유출은 CI dedup 비연결성을 깨는 보안 크리티컬.
- **DR/HA**: PostgreSQL 백업/PITR·Redis 지속성·RTO/RPO 명시.
- **보안 테스트(prod 게이트)**: 위협모델(STRIDE)·펜테스트·SCA(의존성 스캔)·enumeration 저항(로그인/가입 에러 패리티)·timing-safe 비교. 인증+PII 시스템의 비협상 항목.
- **컴플라이언스 게이트**: prod/P4 전 법무 형식 최종검토 + **PIA(개인정보 영향평가) 필요성 판정**을 명문 게이트로.
- **API 계약**: 공개 계약 OpenAPI + 계약 테스트 + 버저닝(`presentation/v{n}`). 소비 서비스엔 계약이 곧 제품.

## 6. 리스크·미해결 질문

- **D6(저장소 토폴로지) — 확정**: 단일 PostgreSQL·스키마 분리(사용자 승인). 온보딩·탈퇴는 단일 트랜잭션으로 원자화돼 사가·고아·복구 원장이 불필요하다. **물리 별도 DB로 분리하는 순간이 사가 재도입 지점** — 그때 "영속 PENDING 금지 vs 내구 재생 소스 필요" 긴장을 유저측 멱등 원장(RegistrationId 디둡) + credential 재료 내구 보관으로 화해시킨다(CI 유니크만으론 부분성공+TTL만료 고아를 못 막음).
- **외부 조달 리드타임 — 크리티컬 패스**: 본인인증 계약·소셜 심사·Apple Developer·법무는 기술 선행조건이 아니라 수 주~수개월 리드타임. P0와 병렬로 지금 시작(§4 P0-6). 지연 시 P2/P4가 밀린다.
- **Redis 진실원본의 failover 정합 — 해소([`REDIS_HA.md`](REDIS_HA.md))**: 비동기 복제 환경에서 무효화가 유실되면 제품 명제(실시간 강제 로그아웃)가 깨진다. 결정문서로 정책 확정(min-replicas 창 축소, WAIT/WAITAOF 완전 차단은 P3/P5 revoke 소비자와 함께). 코드는 fail-closed·keyslotting을 강제하고, 회전 부분성공 창을 문서화된 위협으로 등재.
- **JWT vs opaque**: 매 요청 Redis를 치므로 JWT 무상태 이점은 상당 부분 상쇄된다(실이득=roles/sid 클레임으로 유저 조회 회피). 도메인 모델이 의도적으로 채택했으나, P1a에서 실측으로 이점을 정량화해 유지/재고를 판단한다.
- **D5 공격면(미래 분리 노트)**: PII 유저 도메인과 인터넷 대면 인증이 한 JVM에 있으면 인증 프로세스 침해가 in-process로 PII 복호화에 닿는다. 물리 분리 시 우선 격리 대상으로 기록.
- **KMS 장애 런북**: 복호화 불가 시 본인인증·조회 마비 거동을 온콜 런북에 명시.

## 착수 순서 요약

P0(하네스, 타임박스 + 조달 병렬) → P1a(핫패스 MVP) → P1b(최소 로컬 온보딩, 단일 트랜잭션) → P2(소셜) → P3(디바이스·다중세션) → P4(본인인증 실·약관·탈퇴/보존·휴면) → P5(알림·레이트리밋·잠금) → P6(관리자·RBAC·감사) → P7(MFA 슬롯). 각 단계는 §3 표준 루프를 애그리거트에 적용하고 §4 검증으로 닫는다. §5 횡단 게이트(특히 Redis HA·PII 키/blind index)는 P1a 전 확정한다.

## 진행 현황 (투두)

세션마다 이 목록을 갱신한다. 각 단계는 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 반영 → 커밋·메인 머지 → 새 세션으로 진행한다. **다음 세션은 아래에서 첫 미완 항목을 이어서 진행한다.**

- [x] **Phase 0** — 빌드 하네스 + walking skeleton (`./gradlew build` green)
- [x] **Phase 1a (핵심 슬라이스)** — 인증 핫패스 E2E: 로그인 → O(1) 세션검증 → 로그아웃 즉시 401 → 리프레시 회전 + 유예 창 + 재사용 감지→패밀리 전멸. 신규 모듈 `domain-auth`·`infra-redis`(Lua 회전)·`infra-crypto`(Argon2id/SHA-256)·`common-auth`(Nimbus RS256 JWT)·`common-web`(Spring Security 필터)·`app-api`. throwaway `domain-skeleton` 제거. 28 테스트 green(Testcontainers PG+Redis + HTTP E2E + ArchUnit).
  - [x] **1a 비밀번호 생명주기 슬라이스** — 재설정(`initiateReset`/`completeReset` + `VerificationChallenge` Redis 애그리거트 + Lua 원자검증 + `NotificationSender` Mock 포트) + 최근 N 재사용 금지(`password_history`를 `PasswordCredential` 애그리거트 자식으로, cascade·orphanRemoval) + 로그인 상태 변경. 신규 모듈 `external-notification`(+`convention.external-module` 플러그인). 40 테스트 green. 설계→콜드 설계리뷰(YELLOW)→구현→콜드 코드리뷰(YELLOW, 블로킹 0)→반영.
  - [x] **1a JWKS 엔드포인트 + 90일 회전 슬라이스** — `SigningKeyRing`(common-auth, 인메모리 회전 키링: 현재 서명키 + 유예 창 검증키, read-time `expiresAt>now` 강제, grace 키는 공개키로만 저장) + kid 기반 다중키 발급/검증(`JwtCryptoConfig`가 서명=current-only·검증=`withJwkSource` JWKSource로 재배선) + `/.well-known/jwks.json` 게시(app-api `JwksController`, permitAll, 개인키 미노출). grace ≥ 2×Access TTL 불변식을 빈 조립 시 fail-fast 강제. 49 테스트 green(유닛 회전/유예/만료/kid + E2E 왕복검증). 설계→콜드 설계리뷰(YELLOW)→구현→콜드 코드리뷰(YELLOW, 블로킹 0)→반영.
    - **운영 수용(이연의 귀결, 배포 전 필수 인지)**: 자동 90일 회전 트리거는 의도적 미배선(인메모리 키 위 타이머는 실효 없음). 그 귀결 — (1) 앱 재시작/재배포마다 서명키가 새 kid로 바뀌어 직전 발급 Access(≤15분)가 재시작 직후 401, (2) **단일 인스턴스 한정 정확**(다중 인스턴스는 키·JWKS 불일치로 상호 검증 실패). 둘 다 자동회전·내구 저장·시크릿매니저 주입을 함께 도입하는 후속 워크스트림이 해소. `rotate()` 메커니즘은 그 seam으로 존재·테스트됨.
  - [x] **1a Argon2 동시성 바운드 실행기 + 용량모델 슬라이스** — `ConcurrencyLimiter`(infra-crypto, fair Semaphore·`tryAcquire(timeout)` → 포화 시 `HashingCapacityException` 503, **획득 성공 시에만 permit 반환**·인터럽트 시 플래그 복원+전파(503 마스킹 금지))로 `Argon2PasswordHasher.hash/matches`를 감싸 동시 Argon2를 상한 → 로그인 폭주 시 메모리 폭주(N×~19MB)·CPU 스래싱 방지. 용량모델: `max-concurrent`(0=auto=가용 코어수)·`acquire-timeout-ms`(500), peak Argon2 힙 ≈ permits×memory-kb. `CryptoErrorCode`(HASHING_CAPACITY_EXCEEDED=503)·`HashingCapacityException`은 common-core(포트 계약의 백프레셔 실패 모드) → 기존 `GlobalExceptionHandler`가 503 매핑. 53 테스트 green(barrier 결정적 동시상한=permits·포화차단+permit 누수 없음·예외후 반환·인터럽트 복원). 설계→콜드 설계리뷰(YELLOW)→구현→콜드 코드리뷰(GREEN, 블로킹 0)→MINOR 반영.
    - **결정·이연의 귀결**: (1) 전용 스레드풀 아닌 세마포어 채택 — VT 미설정이라 캐리어 피닝 없음, 실 위험=메모리·CPU를 세마포어가 상한. **VT(`spring.threads.virtual.enabled`) 활성 시 세마포어는 캐리어 피닝을 못 막으므로 전용 플랫폼-스레드 실행기로 교체 필요**(코드 주석에 트리거 명문화). (2) change/resetTo는 tx 내부 KDF라 리미터 포화 시 최악 (historyLimit+2)×타임아웃≈3.5s 커넥션 점유 가능 — 저동시성이라 유계로 수용, **후속 백로그**: change/resetTo KDF를 로그인처럼 tx 밖으로. (3) 실 부하(메모리/지연 실측)는 §5 성능·용량 운영 워크스트림(CI 단위테스트 아님).
  - [x] **1a Redis HA/failover 정합 슬라이스(마지막 횡단 게이트) — DONE** — 범위: fail-closed 핫패스 + Lua keyslotting 검증 + failover 결정문서(사용자 확정, 토폴로지=Cluster). (a) `RedisSessionStore` 쓰기·회전(`create`·`rotate`·`revoke`·`revokeAll`)을 Redis 불가 시 `AuthErrorCode.SESSION_STORE_UNAVAILABLE`(503)로 fail-closed(읽기 `validate`는 기존대로 false→401 유지). 회전은 GET·Lua만 503으로 감싸고 **ROTATED 확정 후 refidx SET은 best-effort**로 분리(완료된 회전을 503/오탐 REUSE로 오보 방지 — 콜드 설계리뷰 M1). (b) `SessionKeys`로 키 스키마 추출 + `SessionKeysTest`가 회전 Lua가 만지는 전 키의 단일 슬롯을 `ClusterSlotHashUtil`로 강제. (c) [`REDIS_HA.md`](REDIS_HA.md)에 토폴로지·AOF·min-replicas·fail-closed/open·타임아웃·회전 부분성공 창·클라 계약 명문화 + `spring.data.redis.timeout: 250ms`. 60 테스트 green(fail-closed 4 + revokeAll 503 + refidx-실패 회전 + keyslot 2). 설계→콜드 설계리뷰(M1 반영)→구현→콜드 코드리뷰(BLOCKING 0, M1 refidx 분기 테스트 보강)→반영.
    - **결정·이연의 귀결**: (1) 토폴로지=Cluster(해시태그 이미 배선, dev/test는 단일 노드). (2) failover revoke 유실 완전 차단(WAIT/WAITAOF)은 강제 로그아웃 소비자 P3·잠금 P5와 함께 배선(현재는 정책 명문화). (3) `revokeAll` 단일 Lua 원자화·Lettuce 연결단 타임아웃(`connectTimeout`/`disconnectedBehavior`)은 후속. (4) 회전 부분성공 창(ROTATED 후 refidx SET 실패→다음 회전 재로그인)은 refidx가 슬롯 밖일 수밖에 없는 구조상 내재적 — fail-open 아니라 재로그인 강등으로 수용.
  - **→ Phase 1a 마감.** 인증 핫패스·비밀번호 생명주기·JWKS 90일 회전·Argon2 바운드 실행기·Redis HA/failover 정합 전부 완료·머지. 다음 착수는 Phase 1b(최소 로컬 온보딩).
- [ ] **Phase 1b** — 최소 로컬 온보딩(`RegistrationSession`·`VerificationChallenge`·최소 `Terms`/`Consent` 시드·Mock 본인인증·`CiRegistry`·`User`), `CreateUser` 단일 크로스스키마 트랜잭션. `usr` 스키마 등장 → `SchemaFlywayFactory`·PII envelope 암호화·전화 blind index 이 단계에서 배선.
- [ ] **Phase 2** — 소셜 로그인 4종 + 연동/해제
- [ ] **Phase 3** — 디바이스 + 다중 로그인 제한(동시 세션 ≤N·최오래 축출) + 강제/원격 로그아웃
- [ ] **Phase 4** — 본인인증(실) + 약관·동의 + 탈퇴/보존 + 휴면
- [ ] **Phase 5** — 이상탐지·알림 + 레이트리밋 + 계정 잠금(연속 실패 카운터→TEMP_LOCKED)
- [ ] **Phase 6** — 관리자 콘솔 + RBAC + 감사
- [ ] **Phase 7** — MFA(옵션, 슬롯만)

### Phase 1a 확정된 구현 결정 (다음 세션 참고)
- 세션 스토어 포트는 `domain-auth`가 선언하고 `infra-redis`가 Lua로 구현한다. `convention.infra-module`은 common-only 기본 + `infra-redis`에만 `domain-auth` 의존 허용(플랜 §1의 Redis 애그리거트 예외). crypto 포트는 `common-core.crypto`가 소유(infra-crypto는 common-only).
- 리프레시 = 불투명 랜덤(256b) + 서버 역인덱스 `refidx:<jtiHash>→userId|sessionId`(sessionTtl). 세션 키는 `{u:userId}` 해시태그로 단일 슬롯. 회전은 슬라이딩 만료(키 TTL·`expiresAt` 필드·인덱스 TTL 정합).
- Bean 배선: `app-api`가 `com.example.auth` 전체를 컴포넌트 스캔(+`@EntityScan`/`@EnableJpaRepositories`는 도메인 패키지). 도메인 서비스=`@Service`, infra/공용 설정=`@Component`/`@Configuration`.
- SB4 모듈 분리 함정(다음 세션 재발): `@EntityScan`=`org.springframework.boot.persistence.autoconfigure`, `TestRestTemplate`=`spring-boot-resttestclient`(+`spring-boot-restclient` 필요, `@AutoConfigureTestRestTemplate`), Redis만 쓰는 IT는 `spring.autoconfigure.exclude`로 DataSource/JPA 오토컨피그 제외. bcprov는 BOM 미관리라 카탈로그 pin.
- JWKS/서명키: `SigningKeyRing`(common-auth)이 서명키 회전을 소유(포트 아님 — 2번째 구현 등장 시 포트화). 서명=current만(`new JWKSet(currentSigningKey())`), 검증=`NimbusJwtDecoder.withJwkSource((sel,ctx)->sel.select(ring.publicJwkSet(Instant.now())))`로 kid 다중키. 두 JWKSource 람다가 매 호출 volatile 스냅샷을 읽어 회전을 빈 재생성 없이 반영. `@Scheduled "90d"`는 부팅 실패(Spring이 `Long.parseLong` — ISO `P90D` 또는 ms만 허용)라 자동회전 도입 시 주의. Nimbus는 common-auth의 `implementation`이라 소비 모듈(app-api 등) 테스트가 Nimbus를 쓰려면 `testImplementation` 명시. 회전 트리거 배선 시 grace≥2×TTL 강제(이미 빈 조립에 있음)·내구 키스토어·다중 인스턴스 공유를 함께 도입.
