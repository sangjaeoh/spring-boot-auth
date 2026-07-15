# TODO 작업 요청 프롬프트

[`todo.md`](./todo.md)의 각 항목을 새 세션에서 작업 요청할 때 쓰는 프롬프트다. 항목 하나를 골라 코드 블록 안 내용을 그대로 붙여넣는다. 프롬프트는 자기완결이라 이 파일의 다른 부분 없이 단독으로 동작한다(레포 규칙은 CLAUDE.md→AGENTS.md가 자동 적용 — 작업 성격에 맞는 `docs/` 문서를 로딩한다). 각 프롬프트는 마지막에 todo.md 체크 갱신·커밋·메인 머지·잔여 브랜치 삭제 단계를 포함한다. 권장 실행 순서는 todo.md가 소유한다.

각 프롬프트의 범위 서술은 압축본이다 — 애그리거트 필드·상태·정책·불변식·오퍼레이션의 정본은 DOMAIN_MODEL.md, 기능 요구의 정본은 REQUIREMENTS.md이며 충돌하면 정본을 따른다. 엔드포인트 URL·요청 형상은 제안이다 — 기존 컨트롤러 관례(`/auth/**` 평탄 경로, problem+json 오류 매핑)와 충돌하면 관례를 따른다. 큰 항목은 수직 슬라이스로 쪼개 슬라이스마다 검증·머지한다.

## 1. CI 파이프라인

```text
[작업] GitHub Actions 빌드 게이트 추가

배경(확인된 사실):
- .github 디렉터리가 없다. 품질 게이트(Spotless·NullAway·Error Prone·ArchUnit·전체 테스트)는 로컬 ./gradlew build에만 존재한다.
- Java 25 툴체인(build-logic convention.java-base), Gradle 9.5(Kotlin DSL + 버전 카탈로그).
- 통합·E2E 테스트는 Testcontainers가 PostgreSQL(postgres:17-alpine)·Redis(redis:7-alpine) 컨테이너를 직접 띄운다 — CI 러너에 Docker가 필요하다(GitHub Actions ubuntu 러너는 내장).

목표: main 푸시와 PR에서 ./gradlew build가 원격으로 강제되고, 실패가 눈에 보인다.

작업 내용:
1. .github/workflows/build.yml: push(main)·pull_request 트리거 → JDK 25 셋업(배포판의 25 지원 확인) → Gradle 캐시(공식 setup-gradle 액션) → ./gradlew build.
2. Testcontainers(PostgreSQL·Redis)가 러너 Docker로 도는지 확인한다(추가 서비스 선언 불필요 — 테스트가 직접 컨테이너를 띄운다).
3. 병합 차단(branch protection)은 리포 설정이라 코드 밖 — README 또는 PR 설명에 권장 설정만 언급한다.
4. 검증: 워크플로를 실제로 1회 통과시킨다(그린 런 확인 없이 끝내지 않는다).

하지 말 것: 배포·릴리스 자동화, 매트릭스 빌드, 커버리지 리포트 연동(요청 밖).

완료 기준: 워크플로 그린 런 1회 확인, 로컬 ./gradlew build 통과.
완료 후: 루트 todo.md의 1번 항목(CI 파이프라인)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인 머지, 잔여 브랜치 삭제.
```

## 2. 가입 완료 — CreateUser 단일 크로스스키마 트랜잭션

```text
[작업] 가입 완료 엔드포인트 + CreateUser 단일 크로스스키마 트랜잭션 — 요구사항 Phase 1 마감

배경(확인된 사실):
- 온보딩 플로우는 완결돼 있다: RegistrationSession(Redis TTL 30분)이 이메일/휴대폰 코드(VerificationChallenge, 챌린지 종별 바인딩)·Mock 본인인증(IdentityVerification 영속, ciHash=blind index, 원문 CI 미저장)·필수동의 버퍼(ConsentSelection, ConsentValidator 조기 검증)를 스텝 마킹하고, RegistrationSessionProcessor.complete()가 LOCAL 스텝셋(이메일∧휴대폰∧본인인증∧필수동의) 충족을 검증해 CreateUser에 필요한 번들(loginEmail·verificationRef·ciHash·동의 선택)을 Info로 반환한다. complete()는 세션을 소비하지 않는다(멱등키=registrationId 보존 — 의도된 설계).
- 그러나 정회원 커밋 라우트가 없다: 가입 완료 엔드포인트 미존재. app-api의 AccountProvisioningFacade는 테스트/시드 전용으로 User+AuthAccount+PasswordCredential을 만든다.
- ConsentRecord(append-only)·ConsentState는 엔티티·테이블이 아직 없다(동의는 세션 버퍼에만 존재). CiRegistry는 엔티티 + ci_hash 유니크 DDL(usr V3) + 읽기 soft-check(CiUniquenessValidator)까지 있고, link writer가 없다.
- 확정 결정(재론 금지): 저장소 토폴로지는 단일 PostgreSQL + 스키마 분리(usr·auth)이며, 온보딩 같은 크로스스키마 정합은 사가가 아니라 한 ACID 트랜잭션으로 원자화한다 — "한 트랜잭션 하나의 애그리거트" 원칙의 문서화된 예외다. 이벤트 기반 분리 제안은 이미 검토·기각됐다.
- docs/architecture.md 규칙: facade는 트랜잭션을 열지 않는다, 도메인 모듈은 타 도메인 의존 금지 — 이 둘과 정합하는 트랜잭션 배치가 이 작업의 핵심 설계 결정이다.

목표: 온보딩 완료 요청 한 번으로 usr(User ACTIVE·ConsentRecord append·CiRegistry.link 유니크 hard-enforce·IdentityVerification.userId 연결) + auth(AuthAccount·PasswordCredential)가 한 트랜잭션에서 원자 생성되고, 중간 실패는 전량 롤백(고아-User 불가), 성공 시 가입→로그인→세션 검증 E2E가 성립한다. 이 작업으로 REQUIREMENTS Phase 1(코어 로컬 인증 MVP)이 완성된다.

작업 내용:
1. 설계 결정 먼저(트레이드오프 명시): 크로스스키마 트랜잭션 메서드를 어느 모듈·계층이 소유할지 — 도메인 상호 의존 금지·facade tx 미개방 규칙과 정합해야 한다. 배치·전파 방식을 결정하고 "문서화된 예외"임을 코드에 명시한다.
2. ConsentRecord 엔티티 + usr 마이그레이션(append-only — update/delete 없는 설계). 동의는 트랜잭션 안에서 현행 약관 재검증 후 append한다(버퍼 검증은 조기 실패용 — 버퍼~완료 사이 약관 개정 경합 대비, 최종 권위는 여기). ConsentState fold 읽기모델은 6번 소관 — 지금 만들지 않는다.
3. CiRegistry.link(ciHash, userId) writer: ci_hash 유니크 hard-enforce — 중복(동일인 활성 가입 존재)이면 가입 거부. 재가입 쿨다운 판정은 6번 소관(현재 tombstone이 생길 경로가 없다).
4. 가입 완료 엔드포인트 신설(온보딩 토큰 인증, 비밀번호 수취 — 기존 PasswordPolicyValidator 정책 검증): complete() 번들 + 비밀번호로 CreateUser 호출 → 성공 후 세션 소비. 같은 registrationId 재요청(중복 완료)의 멱등 처리 방식을 결정하고 근거를 남긴다.
5. loginEmail 중복은 AuthAccount 유니크가 hard-enforce한다 — 거부 표현과 열거 저항(이메일 존재 여부 노출 수준)을 기존 비밀번호 재설정의 열거 저항 관례와 일관되게 결정한다.
6. AccountProvisioningFacade 시드 경로가 정식 CreateUser 경로를 재사용하도록 정리한다(기존 E2E 픽스처가 실제 생성 경로를 타게 — 버리는 코드 금지).
7. 테스트: 중간 실패 주입 시 전량 롤백(User·AuthAccount 고아 없음), ciHash 중복 거부, loginEmail 중복 거부, 스텝 미충족 완료 거부(기존 유지), 완료 멱등, 영속 PENDING 미생성, 가입 완료→로그인→세션 검증 E2E.

하지 말 것: 소셜 온보딩(3번), 재가입 쿨다운·탈퇴(6번), 이벤트 인프라(4번), USER 역할 영속 배정(8번 — 현재 roles 하드코딩 유지).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트(Spotless·NullAway·Error Prone·ArchUnit)가 통과한다.
완료 후: 루트 todo.md의 2번 항목(가입 완료 — CreateUser 단일 크로스스키마 트랜잭션)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인 머지, 잔여 브랜치 삭제.
```

## 3. 소셜 로그인 4종 + 연동/해제

```text
[작업] 소셜 로그인 4종(카카오·네이버·구글·애플) + 연동/해제

배경(확인된 사실):
- SocialConnection 애그리거트·external-social 모듈이 없다. RegistrationSession은 RegistrationType.SOCIAL 스텝셋(본인인증∧필수동의)을 이미 선언하고 있다.
- 2번(CreateUser 단일 트랜잭션) 완료가 전제다 — 소셜 최초 로그인 온보딩이 같은 트랜잭션을 재사용한다.
- JWT 원자재는 common-auth(Nimbus JOSE) 위에 있다. 확정 결정: OIDC id_token 검증 같은 커모디티는 검증된 라이브러리에 위탁한다(자작 금지).
- 외부 어댑터 선례: external-identity(Mock 본인인증)·external-notification(Mock 발송) — 포트는 도메인이 소유하고 dev/test는 Mock으로 오프라인 E2E를 성립시킨다.
- 각 사 앱 등록·심사·Apple Developer(.p8)는 리드타임 있는 외부 조달 — 실 어댑터의 실 호출 검증은 조달에 의존하나 개발은 Mock으로 비블로킹.

목표: 4사 OIDC 로그인이 동작하고(기존 연동 계정은 즉시 세션 발급, 미연동 신규는 SOCIAL 온보딩 유도), 로그인 상태에서 연동/해제가 ≥1 로그인 수단 유지를 강제하며, dev 오프라인 E2E가 성립한다.

작업 내용:
1. external-social 모듈 신설 + SocialIdentityProvider 포트(domain-auth 소유, 벤더 중립): id_token 검증 → subject·email(·릴레이 여부). 카카오/네이버/구글/애플 어댑터 + dev Mock.
2. SocialConnection 애그리거트(auth 스키마 마이그레이션): (provider, providerUserId) 전역 유니크 + (userId, provider) 유니크.
3. 소셜 로그인 엔드포인트: 기존 연동이 있으면 접근 판정(userStatus·lockState) 후 기존 세션 발급 경로 재사용, 없으면 RegistrationSession(SOCIAL) 시작(provisionalContext에 subject·email 보관) → 본인인증 + 필수동의 → 가입 완료가 CreateUser + SocialConnection.connect까지 원자 수행(비밀번호 없는 계정 — PasswordCredential은 0..1).
4. 연동/해제 API + ≥1 로그인 수단 유지(LoginMethodPolicy): 동시 해제 TOCTOU(두 요청이 모두 "하나 남음"을 관측해 0이 되는 경합) 방지 — AuthAccount 행 잠금 등으로 직렬화하고, 동시 해제 2건 중 하나만 성공하는 테스트를 포함한다.
5. 애플 특수사항: .p8 동적 client_secret, id_token 검증, 사용자 정보 최초 1회 제공, 릴레이 이메일(isPrivateRelay)이 contactEmail seed가 되는 흐름.
6. 테스트: 신규 소셜 가입 E2E(Mock), 기존 연동 재로그인, (provider, subject) 중복 연결 거부, 마지막 수단 해제 거부, 동시 해제 직렬화, 릴레이 이메일 seed.

하지 말 것: provider 추가 일반화 과설계, 실 각사 키 없이는 못 도는 구조(Mock 우선), 이벤트 발행 인프라(4번).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다. 실 어댑터의 키 주입 지점은 조달 완료 시 설정만으로 스위치되게 남긴다.
완료 후: 루트 todo.md의 3번 항목(소셜 로그인 4종 + 연동/해제)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인 머지, 잔여 브랜치 삭제.
```

## 4. 이벤트 인프라(common-messaging·infra-messaging)

```text
[작업] 이벤트 인프라 — MessagePublisher 포트 + 멱등 소비 + 내구 재시도(DLQ)

배경(확인된 사실):
- 이벤트는 현재 in-process뿐이다: domain-auth의 이벤트 record 7종(LoggedIn·LoginFailed·PasswordChanged·PasswordResetRequested/Completed·RefreshReuseDetected·SessionRevoked)을 ApplicationEventPublisher로 발행하고 app-api SecurityEventLogger가 구조적 로깅으로 소비한다. 유실 방지·멱등·재시도 장치가 없다.
- common-messaging은 컨벤션 플러그인 화이트리스트에 예약만 돼 있고 모듈은 미생성이다. infra-messaging도 없다.
- 확정 결정(재론 금지): 통합 이벤트는 공개 스키마(직렬화 가능 + 단조 version + occurredAt)로 고정하고, 발행은 @TransactionalEventListener(AFTER_COMMIT) 기반 in-process, 소비는 멱등 + 내구 재시도(DLQ). 아웃박스 릴레이·외부 브로커는 물리 분리/실 브로커 도입 시점의 일 — 지금 만들지 않는다.
- 후속 의존: 5번(ForceLogoutRequested 소비·NewDeviceDetected 발행), 6번(UserWithdrawn·UserStatusChanged·LastLoginObserved), 7번(보안 알림 구독), 8번(RoleChanged·감사 append)이 이 인프라를 쓴다. 통합 이벤트 목록·발행/구독처는 DOMAIN_MODEL.md "도메인 이벤트" 표가 정본이다.

목표: MessagePublisher 포트 뒤에서 커밋 후 이벤트가 전달되고, 소비자가 중복 전달에 멱등하며, 소비 실패가 DLQ에 남아 재시도되는 기반이 선다.

작업 내용:
1. common-messaging 신설: MessagePublisher 포트 + 통합 이벤트 공개 스키마 규약(직렬화·단조 version·occurredAt·이벤트 ID) + 멱등 소비 지원(이벤트 ID 디둡) + DLQ 계약.
2. infra-messaging 신설: in-process transport(AFTER_COMMIT 발행) + 소비 실패 DLQ 테이블·재시도. DLQ/디둡 테이블의 스키마 소유를 docs/architecture.md 모듈 규칙과 정합하게 결정하고 근거를 남긴다(전용 스키마 신설이면 SchemaFlywayFactory·app.migration.schemas 등록 포함).
3. 기존 domain-auth 이벤트 발행 지점을 이 포트로 외과적으로 정리한다(SecurityEventLogger 소비는 유지). 통합/도메인 이벤트 구분은 DOMAIN_MODEL 표를 따르고, 구독자 없는 이벤트의 소급 발행을 새로 발굴하지 않는다.
4. per-userId 순서·멱등 소비 계약을 소비자 규약으로 문서화하고, 코드로 강제 가능한 부분(디둡)은 강제한다.
5. 테스트: 발행 트랜잭션 롤백 시 미전달, 중복 전달 시 1회만 처리(디둡), 소비 실패 → DLQ 적재 → 재시도 성공, AFTER_COMMIT 발행 순서.

하지 말 것: Kafka 등 외부 브로커·CDC·아웃박스 릴레이 도입, 전 도메인 이벤트 전면 재배선, 신규 이벤트 발굴.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 4번 항목(이벤트 인프라)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인 머지, 잔여 브랜치 삭제.
```

## 5. 디바이스 + 다중 로그인 제한 + 강제/원격 로그아웃

```text
[작업] Device 애그리거트 + 동시 세션 상한·축출 + 세션 목록/원격·강제 로그아웃

배경(확인된 사실):
- 세션(Redis 진실원본)·O(1) 검증·revoke/revokeAll(SessionProcessor)·리프레시 회전 Lua는 구현돼 있으나, Device 엔티티가 없고(세션의 deviceId는 placeholder), 동시 세션 상한·최오래 축출·내 세션 목록·원격 로그아웃 라우트가 없다.
- Redis 확정 결정: 토폴로지=Cluster 전제, 세션 키는 {u:userId} 해시태그 단일 슬롯, Lua가 만지는 전 키의 단일 슬롯을 SessionKeysTest가 강제한다(이 규약 유지 의무). 쓰기·회전은 fail-closed 503, command timeout 250ms. failover 시 revoke 유실 완전 차단(WAIT/WAITAOF)은 강제 로그아웃 소비자와 함께 이 작업에서 배선하기로 이연된 결정이다.
- 4번(이벤트 인프라) 완료가 전제다: ForceLogoutRequested 소비, NewDeviceDetected·ConcurrentLimitExceeded 발행.
- 정책값: 동시 활성 세션 상한 3(확정) — 설정으로 외부화한다. 오퍼레이션 정본은 DOMAIN_MODEL §2.4(AccountSessions)·§2.5(Device).

목표: 로그인 시 기기가 인식·등록되고, 동시 활성 세션이 상한을 원자적으로 넘지 않으며(초과 시 최오래 축출), 사용자가 내 세션을 조회·원격 종료하고, 관리자 강제 종료 이벤트로 전 세션이 즉시 무효화된다.

작업 내용:
1. Device 애그리거트(auth 스키마 마이그레이션): (userId, fingerprint) 유니크, 기기명·플랫폼·최근 IP/시각·신뢰 플래그·푸시 토큰/권한(pushEnabled). 등록·내 기기 목록·신뢰/해제·푸시 토큰/권한 API.
2. DeviceRecognitionService: 세션 생성 전 지문 매칭으로 기존/신규 판정, 신규면 DeviceRegistered/NewDeviceDetected 발행(알림 발송 자체는 7번). 세션의 deviceId placeholder를 실 기기 바인딩으로 교체.
3. 동시 세션 ≤N(설정, 기본 3): createSession에 Redis Lua 원자 검증 + 최오래(issuedAt) 축출, ConcurrentLimitExceeded 발행. 단일 슬롯 규약(SessionKeysTest) 유지.
4. 내 세션 목록 API(기기·IP·최근 접속) + 특정 세션 revoke·현재 제외 전체 revokeAllExcept.
5. 기기 삭제 → revokeByDevice(해당 기기 세션 종료).
6. ForceLogoutRequested 통합 이벤트 소비 → revokeAll(발행측 관리자 API는 8번).
7. revoke 계열의 failover 유실 차단(WAIT/WAITAOF) 배선 — 무효화가 복제 확인 전 유실되지 않게. 실패 시 거동은 기존 fail-closed 503 정책과 일관되게.
8. 테스트: N+1 로그인 시 최오래 세션 즉시 401, 원격/강제 종료 즉시 401, 기기 삭제 후 그 기기 세션 401, 신규 기기 이벤트 발행, 동시 로그인 경합에서 상한 초과 불가.

하지 말 것: 알림 발송 구현(7번), 관리자 콘솔 API(8번), GeoIP·riskScore 산출(7번).

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 5번 항목(디바이스 + 다중 로그인 제한 + 강제/원격 로그아웃)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인 머지, 잔여 브랜치 삭제.
```

## 6. 본인인증(실) + 약관·동의 정식화 + 내 정보 + 탈퇴·보존 + 휴면

```text
[작업] 본인인증(실 어댑터) + 약관·동의 정식화 + 내 정보 + 탈퇴·보존·재가입 쿨다운 + 휴면 + app-batch

배경(확인된 사실):
- 본인인증은 Mock(external-identity, 결정적 CI 파생)만 있고 실 기관 어댑터가 없다. 포트 IdentityVerificationProvider는 domain-user 소유로 이미 격리돼 있다.
- 약관은 TermsDocument/TermsVersion + 필수 3종(SERVICE·PRIVACY_REQUIRED·AGE14) v1 시드 + ConsentValidator(가입용)만 있다 — 버전 발행 오퍼레이션·재동의 게이트·이용 중 동의/철회·ConsentState fold 읽기모델이 없다(ConsentRecord append는 2번에서 생성됨).
- NotificationPreference·탈퇴·휴면·재가입 쿨다운·보존 파기·app-batch가 없다. AuthAccount.userStatus는 스냅샷 필드만 있고 동기화 writer가 없다. 내 정보 조회·수정 API가 없다(GET /auth/me는 토큰 주체 확인뿐).
- 이연분(이 작업 소관): IdentityVerification EXPIRED 스윕(포트 런타임 예외 시 REQUESTED 잔존을 이 스윕이 수렴 — FAILED 오분류 금지), 재가입 쿨다운 판정(CiUniquenessValidator에 tombstone 분기).
- 4번(이벤트 인프라) 완료가 전제다: UserWithdrawn·UserStatusChanged(단조 version)·LastLoginObserved 통합 이벤트.
- 정책값(확정, RetentionPolicy 등 설정 외부화 — 하드코딩 금지): 휴면 12개월 + 30일 전 사전통지, 재가입 쿨다운 30일, CI tombstone 6개월, 동의이력 탈퇴 후 5년, 로그인이력·IP 2년(IP 90일 후 가명화). 보존·파기 정본은 DOMAIN_MODEL "보존·파기 스케줄".

목표: 실 기관 스위치가 도메인 무변경으로 가능하고, 약관 개정→재동의 게이트가 동작하며, 회원이 내 정보를 조회·수정하고, 탈퇴가 PII 즉시 파기 + CI tombstone + 전 세션·자격증명 정리로 완결되며(직후 신규 로그인 fail-closed), 휴면 전환·해제(OTP)와 보존 파기가 배치로 돈다.

작업 내용(수직 슬라이스 분할 권장 — 실 본인인증 / 약관·동의 / 내 정보 / 탈퇴·쿨다운 / 휴면·배치. 슬라이스마다 검증·머지):
1. external-identity 실 기관 어댑터 배선(계약 기관 기준, 포트 계약 유지) + Mock↔실 피처플래그 스위치가 도메인 무변경임을 검증. 조달 미완이면 어댑터 골격 + 설정 스위치까지 완성하고 실 호출 검증은 조달 완료 후 항목으로 명시한다.
2. 약관: publishVersion((type,version) 유니크·단조 증가·발행 후 불변) + 필수 새 버전 발행 시 재동의 필요 판정(ConsentPolicy — 이용 게이트가 최신 필수버전 동의를 검증) + 이용 중 동의/철회 API(철회는 선택 약관만 — 필수 철회는 탈퇴 경로 안내) + ConsentState fold 읽기모델((userId, termsType) 유니크).
3. NotificationPreference 애그리거트 생성(채널×카테고리 opt-in, SECURITY 최소 채널 해제 거부) + 마케팅 동의↔매트릭스 동기화(syncMarketingFromConsent). 수신설정 API·발송 판정은 7번 소관.
4. 내 정보: 조회(마스킹 정책 적용)·contactEmail 변경(loginEmail과 독립)·loginEmail 변경(유니크). 프로필(이름·생년·성별·휴대폰)은 본인인증 결과로만 채워지는 불변을 유지한다.
5. 탈퇴: withdraw → WITHDRAWN + PII 즉시 파기(법정 보존분 제외) + CiRegistry.retainOnWithdrawal(tombstone) + UserWithdrawn 발행 → 인증이 세션 revokeAll·자격증명/소셜/기기 정리. 신규 로그인 창 닫기: 탈퇴가 인증-로컬 userStatus를 동기 플립하거나 로그인/세션 생성이 pending-withdrawal에 fail-closed — 파기된 정체성에 fresh 세션이 발급되지 않아야 한다.
6. 재가입 쿨다운: CiUniquenessValidator가 tombstone의 withdrawnAt + cooldown(30일)으로 거부·잔여일 반환. CreateUser의 link hard-enforce와 정합.
7. userStatus 스냅샷 동기화: UserStatusChanged/UserWithdrawn 소비(단조 version·멱등)로 AuthAccount.applyUserStatus. 휴면(DORMANT) 해제는 유저 권위의 OTP 재인증으로만 — 스냅샷이 ACTIVE로 보여도 우회 금지(안전 비대칭은 WITHDRAWN 전용).
8. 휴면: LastLoginObserved 소비로 lastLoginAt 갱신, 12개월 미접속 전환 + 30일 전 사전통지, 해제(OTP) 플로우.
9. app-batch 모듈 신설: 휴면 전환 잡·사전통지 잡·보존 파기 잡(RetentionPolicy 외부화 — append-only 3종은 레코드·순서 유지 + PII만 crypto-shred/가명화, IdentityVerification EXPIRED 스윕, CI tombstone 보존창 경과 파기, 로그인이력 IP 90일 가명화). docs/architecture.md의 app-batch 격리 구역(infrastructure/reader) 규칙 준수.
10. 테스트: Mock↔실 스위치 도메인 무변경, 필수 새 버전 발행 시 재동의 게이트, 필수 약관 철회 거부, 탈퇴 직후 신규 로그인 거부 + 기존 세션 전멸 + 평문 PII 잔존 없음, 쿨다운 내 재가입 거부·잔여일 반환, DORMANT는 스냅샷 지연에도 OTP 없이 로그인 불가, 배치 경계값(12개월·30일·보존창) 전환·파기.

하지 말 것: 알림 실 발송·발송 판정(7번), 수신설정 API(7번), 관리자 잠금/해제·감사(8번).

완료 기준: 슬라이스별 테스트 전부 통과, ./gradlew build 게이트 통과. prod 전 법무 형식 최종검토·PIA 필요성 판정이 외부 게이트로 남아 있음을 완료 메모에 명시한다.
완료 후: 루트 todo.md의 6번 항목(본인인증(실) + 약관·동의 정식화 + 내 정보 + 탈퇴·보존 + 휴면)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인 머지, 잔여 브랜치 삭제.
```

## 7. 이상탐지·알림 + 레이트리밋 + 계정 잠금

```text
[작업] 알림 도메인(domain-generic) + 이상탐지(GeoIP·riskScore) + 레이트리밋 + 연속 실패 잠금

배경(확인된 사실):
- 알림은 NotificationSender 포트(domain-auth 소유, EMAIL/SMS 채널) + MockNotificationSender뿐이다 — 발송 이력 애그리거트(Notification)·발송 판정 정책·실 어댑터·PUSH 채널이 없다. domain-generic 모듈 자체가 없다.
- LoginAttempt는 riskScore 필드까지 영속하나 산출기(RiskEvaluator)·GeoIP 포트가 없다. AuthAccount.lockState(NONE·TEMP_LOCKED·ADMIN_LOCKED)와 로그인 접근 판정은 있으나 잠금 전이 writer(연속 실패 카운터)가 없다. 레이트리밋이 전무하다.
- 1b 이연분(이 작업 소관): 인증코드 재발송 쿨다운·일일 발송 한도, 민감 엔드포인트 레이트리밋.
- 4번(이벤트 인프라) 전제: 보안 이벤트(RefreshReuseDetected·PasswordChanged·AccountLocked·NewDeviceDetected·ConcurrentLimitExceeded·UserWithdrawn 등) 구독→발송. 6번 전제: NotificationPreference(수신 동의 매트릭스)·contactEmail/contactPhone(수신자 진실원본 — 인증 loginEmail 미사용).
- 정책값(설정 외부화): 연속 실패 잠금 임계 5회·쿨다운 30분, 인증코드 TTL 5분·시도 5회·일일 한도 채널별.

목표: 보안 이벤트가 수신설정·기기권한·카테고리 AND 판정을 거쳐 알림으로 발송되고(보안 카테고리는 opt-out 무시), 신규 기기/지역 로그인이 riskScore에 반영되며, 민감 엔드포인트가 레이트리밋으로 보호되고, 연속 실패가 계정을 일시 잠금(쿨다운 자동 해제)한다.

작업 내용(수직 슬라이스 분할 권장 — 알림 도메인 / 이상탐지 / 레이트리밋·잠금):
1. domain-generic 모듈 신설(generic 스키마 + SchemaFlywayFactory·app.migration.schemas 등록): Notification 애그리거트(PENDING→SENT/FAILED·retryCount·templateId·payload). NotificationSender 포트의 최종 소유를 결정한다(DOMAIN_MODEL은 제네릭 소유 — 기존 domain-auth 포트와의 정리 방식을 설계로 결정하고 근거를 남긴다).
2. external-notification 실 어댑터(이메일 SMTP/SES·SMS 발송대행·푸시 FCM/APNs) 골격 + 피처플래그 — Mock 유지, 실 키는 조달 후 주입.
3. NotificationDispatchPolicy: 발송 = NotificationPreference(유저) AND (PUSH면 Device.pushEnabled ∧ pushToken 존재) AND 카테고리 정책. 수신자 주소는 유저 contactEmail/contactPhone만. SECURITY는 opt-out 무시 + 최소 EMAIL/SMS 강제(강제 이벤트 열거는 DOMAIN_MODEL §3.1). 발송 실패 재시도는 4번 DLQ 인프라 재사용.
4. 알림 수신설정 API(채널×카테고리 opt-in/out — 6번에서 만든 애그리거트 위에, SECURITY 최소 채널 해제 거부).
5. 이상탐지: GeoIpLookup 포트 + dev Mock, RiskEvaluator(신규 기기/신규 지역/실패 이력 → riskScore 산출·LoginAttempt 기록), 신규 기기/지역 로그인 알림. 위험 임계 초과 스텝업은 훅 자리만(MFA 슬롯 — 구현 금지).
6. 레이트리밋: 민감 엔드포인트(로그인·코드 발송·재설정) IP/계정 기준 + 인증코드 일일 발송 한도·재발송 쿨다운 → 초과 429. Redis 카운터+TTL.
7. 계정 잠금: 연속 실패 카운터(Redis+TTL — 이력과 분리) → 임계 초과 시 AuthAccount.lock(TEMP_LOCKED) + AccountLocked 발행(보안 알림), 쿨다운 경과 자동 해제, 성공 로그인 시 카운터 리셋. 기존 로그인 접근 판정(lockState 검사)과 정합.
8. 테스트: opt-out 반영 + 채널 AND, 보안 알림이 수신거부에도 발송, SECURITY 최소 채널 해제 거부, 신규 지역 로그인 riskScore 상승·알림, 임계 초과 429, N회 실패 TEMP_LOCKED → 쿨다운 후 해제 → 성공 시 카운터 리셋, 발송 실패 재시도(DLQ).

하지 말 것: 관리자 잠금/해제 API(8번 — ADMIN_LOCKED 전이는 8번), MFA 스텝업 구현, 실 발송 벤더 계약에 의존하는 검증.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 7번 항목(이상탐지·알림 + 레이트리밋 + 계정 잠금)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인 머지, 잔여 브랜치 삭제.
```

## 8. 관리자 콘솔 + RBAC + 감사

```text
[작업] 관리자 콘솔(app-admin) + RBAC(배정=유저·집행=인증) + 감사(AuditLog WORM)

배경(확인된 사실):
- Role/Permission/user_role이 없고 roles는 AuthFacade에 ["USER"]로 하드코딩돼 있다(JWT roles 클레임 원자재는 common-auth에 있음). app-admin 모듈·AuditLog 영속(현재 SecurityEventLogger 구조적 로깅뿐)·관리자 API가 없다.
- ADMIN_LOCKED 잠금 전이·해제, 관리자 강제 로그아웃 발행(소비는 5번에서 배선됨), 실효 회원상태 합성(생명주기×잠금)이 없다.
- 4번(이벤트 인프라) 전제: RoleChanged 발행→인증 클레임 갱신, 통합 이벤트→감사 append. 회원 검색은 전화 blind index(HmacBlindIndexer)를 활용할 수 있다.
- docs/architecture.md: app-admin은 infrastructure/query 격리 구역에서만 크로스 스키마 read(읽기 전용)를 허용한다.
- 정책: 배정은 유저(usr 스키마), 집행은 인증(토큰 클레임). 실효 상태 우선순위 WITHDRAWN > LOCKED > DORMANT > ACTIVE(두 축을 한 컬럼에 병합하지 않는다).

목표: 역할·권한 기반으로 관리자 API가 통제되고, 관리자가 회원 검색(마스킹)·강제 로그아웃·잠금/해제·로그인 이력 조회·권한 변경을 수행하며, 인증 이벤트·관리자 행위가 AuditLog(WORM, 전/후 값)에 남는다.

작업 내용(수직 슬라이스 분할 권장 — RBAC / app-admin / 감사):
1. RBAC(usr 스키마): Role(USER·ADMIN·SUPER_ADMIN, name 유니크)·Permission((resource,action) 유니크)·role_permission·user_role((userId,roleId) 유니크). 가입 시 USER 자동 배정(CreateUser 트랜잭션에 편입) + AuthFacade의 하드코딩 roles를 실 배정 조회로 교체(기존 계정 backfill 방법 결정 포함). 관리자 시딩 방법을 결정한다(마이그레이션 시딩 vs 로컬 프로필 시딩).
2. RoleChanged 발행 → 인증이 토큰 클레임 갱신. 즉시 반영 수준(재발급 시 반영 vs 세션 무효화)을 결정하고 근거를 남긴다.
3. 관리자 API 접근 통제: 토큰 roles 클레임 기반 집행 — 관리자 API는 역할·권한 가드, 비관리자 403, 미인증 401.
4. app-admin 모듈 신설: 회원 검색(전화 blind index·이메일)/상세 — 민감정보 마스킹 정책 적용 + 실효 회원상태 표시(effective = f(lifecycle, lock)), 강제 로그아웃(ForceLogoutRequested 발행 — 소비·전멸은 5번에서 배선됨), 계정 잠금/해제(ADMIN_LOCKED 전이), 로그인 이력 조회, 권한 변경. 크로스 스키마 read는 infrastructure/query 격리 구역에만 둔다.
5. AuditLog(generic 스키마): append-only WORM — update/delete 오퍼레이션을 리포지토리 표면에 두지 않고 테스트로 강제, 관리자 행위·권한 변경은 before/after 기록, 인증 이벤트(로그인/로그아웃/재발급/강제 로그아웃) 구독 append(4번 인프라), 조회 API. 보존 2년 후 PII crypto-shred는 6번에서 만든 app-batch 잡에 연결한다.
6. 테스트: 비관리자 관리자 API 403·관리자 성공, 검색 결과 마스킹, 강제 로그아웃 즉시 401, ADMIN_LOCKED 로그인 거부·해제 후 허용, AuditLog 수정 시도 차단 + 전/후 값 기록, 실효 상태 합성(휴면+잠금 등 조합) 렌더, RoleChanged 후 클레임 반영.

하지 말 것: 관리자 UI(화면), 세분화 권한 매트릭스 과설계(요구는 역할 3종 + 권한 매핑), 외부 SIEM 연동.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 8번 항목(관리자 콘솔 + RBAC + 감사)을 체크([ ] → [x])로 갱신한다. 커밋 및 메인 머지, 잔여 브랜치 삭제.
```

## 9. 이연 하드닝(1a·1b)

```text
[작업] 이연 하드닝 — JWKS 내구화·자동 회전, Redis 원자화·타임아웃, KDF tx 분리, 오류 표현 정합

배경(확인된 사실 — 전부 의도적 이연분):
- JWKS/서명키: SigningKeyRing이 인메모리라 (1) 재시작마다 새 kid — 직전 발급 Access(≤15분)가 재시작 직후 401, (2) 단일 인스턴스 한정(다중 인스턴스는 키·JWKS 불일치로 상호 검증 실패), (3) 자동 90일 회전 트리거 미배선. rotate() 메커니즘과 grace(≥2×Access TTL) fail-fast 빈 조립은 존재한다.
- 함정: @Scheduled에 "90d"는 부팅 실패한다(Spring은 ISO "P90D" 또는 ms만 허용).
- revokeAll이 단일 Lua 원자화가 아니다. Lettuce 연결단 타임아웃(connectTimeout·disconnectedBehavior)이 미설정이다(현재 command timeout 250ms만).
- PasswordCredential change/resetTo가 KDF(Argon2)를 트랜잭션 안에서 수행한다 — 리미터 포화 시 커넥션 장기 점유 가능(로그인 경로는 tx 밖으로 이미 분리됨).
- VerificationChallengeStore.issue는 Redis 장애 시 raw 500이다(다른 쓰기 경로는 503 fail-closed로 정리됨).
- 로그인 DTO의 Jakarta @Email은 점 없는 도메인을 통과시켜 도메인 Email 생성 IAE가 500이 된다(온보딩 DTO는 도메인 동치 @Pattern으로 정리됨).
- 조건부: Argon2 ConcurrencyLimiter는 세마포어 기반 — spring.threads.virtual.enabled 활성 시 캐리어 피닝을 못 막으므로 전용 플랫폼 스레드 실행기로 교체가 필요하다(코드 주석에 트리거 명문화). VT 활성 계획이 없으면 건드리지 않는다.

목표: 앱 재시작·다중 인스턴스·자동 회전에서 토큰 검증이 연속되고, revoke 원자성·연결 실패 거동·트랜잭션 점유·오류 표현의 이연 부채가 해소된다.

작업 내용(항목별 독립 슬라이스 가능 — 각각 검증·머지):
1. JWKS: 내구 키스토어(저장 위치·시크릿 주입 방식을 설계 결정, 최소안 우선) + 다중 인스턴스 키·JWKS 공유 + 자동 90일 회전 트리거를 함께 도입한다(셋을 분리 도입하면 배경의 401·불일치가 해소되지 않는다). grace ≥ 2×Access TTL 불변식 유지.
2. revokeAll 단일 Lua 원자화(단일 슬롯 규약·SessionKeysTest 유지) + Lettuce connectTimeout·disconnectedBehavior 설정.
3. change/resetTo의 KDF를 로그인처럼 트랜잭션 밖으로 이동.
4. 챌린지 발급의 Redis 장애를 503 fail-closed로 정합.
5. 로그인 DTO @Email 간극을 온보딩과 동일한 @Pattern으로 정합.
6. (VT 활성 계획이 있을 때만) Argon2 실행기를 전용 플랫폼 스레드로 교체.
7. 테스트: 재시작(새 컨텍스트) 후 직전 발급 Access가 grace 창 내 유효, 두 컨텍스트(다중 인스턴스 시뮬레이션) 상호 토큰 검증, 자동 회전 후 구키 grace 검증 유지, revokeAll 원자성, KDF 이동 후 재설정/변경 플로우 회귀.

하지 말 것: KMS 등 신규 인프라 전제 과설계(최소안 우선), 무관 리팩터.

완료 기준: 위 테스트가 통과하고 ./gradlew build 게이트가 통과한다.
완료 후: 루트 todo.md의 9번 항목(이연 하드닝(1a·1b))을 체크([ ] → [x])로 갱신한다. 커밋 및 메인 머지, 잔여 브랜치 삭제.
```

## 10. 운영·보안 게이트(prod 배포 전 비협상)

```text
[작업] 운영·보안 게이트 실사·구축 — prod 배포 전 비협상 항목

배경(확인된 사실):
- 미배선: 부하테스트, 관측성(Actuator·메트릭·SLO·보안 탐지 지표 대시보드), 앱 컨테이너화·CD(1번은 CI 게이트만), 시크릿 관리(현재 봉투암호 KEK·blind index pepper가 application.yml에 dev 정적 키로 평문 — JWT 서명키·DB 자격증명 포함), DR/HA 문서(백업/PITR·RTO/RPO·Redis 지속성), 보안 테스트(STRIDE·펜테스트·SCA), OpenAPI 계약.
- 정본: REQUIREMENTS.md 비기능 요구사항(보안·컴플라이언스·성능·확장성·가용성/운영·테스트 용이성).
- pepper 유출은 CI 중복방지의 비연결성을 깨는 보안 크리티컬 — 시크릿 관리가 최우선 후보다.
- 외부 의존: 법무 형식 최종검토·PIA 필요성 판정, 펜테스트 — 코드 밖 조달·조직 항목은 오너·기한을 문서화한다.

목표: prod 배포를 막는 운영·보안 갭이 실사로 열거되고, 코드로 해소 가능한 갭이 닫히며, 외부 의존 항목은 오너·기한이 있는 게이트로 문서화된다.

작업 내용:
1. 갭 실사 먼저: 아래 축별 현재 상태(완료/부분/미착수)를 레포에서 확인해 우선순위 갭 목록을 만든다 — 시크릿 관리 / 관측성·SLO(로그인 p99·가용성·재사용 감지 급증·잠금율 지표) / 부하·용량(세션 핫패스·Lua 경합·Argon2 메모리 예산·Redis 메모리 모델 실측) / 컨테이너화·CD(app-migration init 컨테이너·Flyway expand-contract 규약(PII 컬럼 드롭 롤백 불가)·Mock↔실 피처플래그·카나리) / DR·HA(백업/PITR·RTO/RPO·복호화 불가 런북) / 보안 테스트(STRIDE·SCA 파이프라인 편입·열거 저항 에러 패리티·timing-safe 비교 점검) / OpenAPI 계약·presentation/v{n} 버저닝 / 컴플라이언스(법무·PIA).
2. 코드로 해소 가능한 갭부터 독립 슬라이스로 진행한다(각 슬라이스 검증·머지). 조직/외부 의존 항목은 오너·기한·차단 대상을 문서로 남긴다.
3. 검증: 각 슬라이스의 검증 기준을 갭 목록에 정의하고 통과시킨다(예: 시크릿 — 평문 키 레포 제거 + 주입 경로 테스트, 관측성 — health/메트릭 응답, 부하 — 목표 p99 실측 리포트).

하지 말 것: 특정 벤더 인프라(K8s·APM 등) 전제 과설계 — 최소안 우선, 요청 밖 기능 추가.

완료 기준: 갭 목록이 전부 "해소" 또는 "외부 의존(오너·기한)"으로 분류되고, 코드 갭 슬라이스가 ./gradlew build 게이트를 통과한다.
완료 후: 루트 todo.md의 10번 항목(운영·보안 게이트)을 체크([ ] → [x])로 갱신한다(잔여 외부 의존은 항목 옆에 메모). 커밋 및 메인 머지, 잔여 브랜치 삭제.
```
