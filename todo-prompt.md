# TODO 착수 프롬프트

[`todo.md`](todo.md)의 각 항목을 **새 세션에서** 시작할 때 아래 해당 프롬프트를 복사해 붙여넣는다. 각 프롬프트는 작업 완료 후 `todo.md` 체크와 `IMPLEMENTATION_PLAN.md` 진행 현황 갱신을 포함한다.

---

## 0. 외부 조달 워크스트림 트래킹

```text
todo.md 0번 항목(외부 조달 워크스트림 트래킹)을 진행해줘.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§4 Phase 0의 6번 병렬 착수, §6 "외부 조달 리드타임"), REQUIREMENTS.md(외부 연동, 제약·전제).

할 일: 프로젝트 루트에 PROCUREMENT.md를 작성해 아래 조달 항목별 트래킹 표를 만들어줘.
- 본인확인기관 계약(NICE/KG모빌리언스/다날 중 선정·사이트코드 발급) — P4 실 어댑터의 전제
- 소셜 4사 개발자앱 등록·심사(카카오·네이버·구글·애플) — P2의 전제
- Apple Developer Program 등록 + .p8 키 발급 — P2 애플의 전제
- 법무: 개인정보 처리방침·약관 원문 확정, PIPA 형식 검토, PIA(개인정보 영향평가) 필요성 판정 — P4/prod 게이트
각 항목에 오너·상태·리드타임 추정·블로킹 대상 Phase·다음 액션 컬럼을 두고, 아직 미정인 값은 "미정"으로 명시해줘(조용히 채우지 말 것).

완료 후: todo.md 0번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"에 조달 트래킹 문서 생성 사실을 한 줄 기록한 뒤 커밋해줘.
```

---

## 1. Phase 1b — 온보딩 플로우 슬라이스

```text
IMPLEMENTATION_PLAN.md "진행 현황"의 "1b 온보딩 플로우 슬라이스" 작업을 진행해줘.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§3 표준 루프, §4 Phase 1b, "Phase 1a/1b 확정된 구현 결정"), DOMAIN_MODEL.md(§2.7 VerificationChallenge, §2.8 RegistrationSession, §1.2 IdentityVerification, §1.3 CiRegistry, §1.4 약관, §1.5 동의, 크로스 플로우 "회원가입 온보딩(LOCAL)"), docs/의 규칙 4문서.

범위:
- RegistrationSession(Redis TTL 30~60분) 애그리거트 + 스텝 마킹(LOCAL 스텝셋: 이메일인증 ∧ 휴대폰인증 ∧ 본인인증 ∧ 필수동의). 이메일/휴대폰 코드는 기존 VerificationChallenge를 재사용한다.
- Mock 본인인증: 신규 external-identity 모듈 + IdentityVerificationProvider 포트(domain-user 소유), IdentityVerification 엔티티(usr, 결과 PII 암호화) + CiRegistry 엔티티(soft-check 읽기 조회만 — 유니크 hard-enforce는 다음 슬라이스의 CreateUser 트랜잭션 소관).
- 필수동의 버퍼: Terms 시드 최소셋 + 동의값은 RegistrationSession에 버퍼링(userId 없는 ConsentRecord 선-기록 금지).
- 온보딩 전용 토큰(정식 Access/Refresh 아님).
- 영속 PENDING 금지 — 정회원(User/AuthAccount) 생성은 이 슬라이스 범위 밖(다음 슬라이스).

검증: 스텝 미충족 시 complete 거부, TTL 만료 시 자동 파기, RegistrationSession에 PII 원문 미보유(verificationRef만), ./gradlew build green.

진행 절차: 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 지적만 반영 → docs/architecture.md "빌드가 강제하는 불변식" 자기검증 → 커밋·main 머지.

완료 후: todo.md 1번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"의 해당 항목을 [x]로 갱신하면서 이 슬라이스의 확정된 구현 결정·이연 사항을 다음 세션이 이어받을 수 있게 기록해줘.
```

---

## 2. Phase 1b — CreateUser 단일 크로스스키마 트랜잭션 슬라이스

```text
IMPLEMENTATION_PLAN.md "진행 현황"의 "1b CreateUser 단일 크로스스키마 트랜잭션 슬라이스" 작업을 진행해줘. 이 슬라이스로 요구사항 Phase 1(코어 로컬 인증 MVP)이 완성된다.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§0 D6, §4 Phase 1b, "Phase 1b 확정된 구현 결정" — 특히 CreateUser 관련 재론 방지 메모), DOMAIN_MODEL.md(§1.1 User register, §1.3 CiRegistry link, §1.5 온보딩 버퍼링, §2.8 complete, 크로스 플로우 "회원가입 온보딩(LOCAL)" 5~6단계), docs/의 규칙 4문서.

범위:
- CreateUser: UserId 채번 → User(ACTIVE) → ConsentRecord append → CiRegistry.link(ciHash 유니크 hard-enforce) → IdentityVerification.userId 연결 → AuthAccount + PasswordCredential 생성을 usr+auth 크로스스키마 한 ACID 트랜잭션으로(D6 승인 예외 — "한 트랜잭션 하나의 애그리거트"의 문서화된 예외임을 코드에 명시).
- 트랜잭션 소유자는 도메인 서비스(facade는 tx 미개방). 이벤트 기반 분리 제안은 D6로 이미 기각된 결정이므로 재론하지 않는다.
- RegistrationSession.complete()가 스텝셋 충족 검증 후 CreateUser를 호출하고 성공 시 온보딩 세션 파기 + 정식 로그인 가능 상태로.
- AccountProvisioningFacade의 seed 경로를 정식 진입점으로 승격.
- app-api를 SchemaFlywayFactory + EMF-dependsOn 순서화로 전환(usr 엔티티 스캔 시 ddl-auto=validate 전 usr 마이그레이션 필요 — "Phase 1b 확정된 구현 결정" 참고).

검증: 중간 실패 주입 시 전량 롤백(고아-User 불가), ciHash 중복 시 가입 거부, 영속 PENDING 미생성, 가입 완료 → 로그인 → 세션 검증 E2E, ./gradlew build green.

진행 절차: 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 지적만 반영 → "빌드가 강제하는 불변식" 자기검증 → 커밋·main 머지.

완료 후: todo.md 2번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"에서 Phase 1b를 완료([x])로 갱신하면서 확정된 구현 결정·이연 사항을 기록해줘.
```

---

## 3. Phase 2 — 소셜 로그인 4종 + 연동/해제

```text
IMPLEMENTATION_PLAN.md의 Phase 2(소셜 로그인 4종 + 연동/해제)를 진행해줘.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§0 D7, §4 Phase 2, "확정된 구현 결정" 절들), DOMAIN_MODEL.md(§2.3 SocialConnection, §2.8 RegistrationSession SOCIAL 스텝셋, 크로스 플로우 "소셜 최초 로그인 온보딩"), REQUIREMENTS.md(소셜 로그인 절), docs/의 규칙 4문서.

범위(§3 표준 루프를 애그리거트별로 적용, 필요 시 슬라이스 분할해 각 슬라이스마다 리뷰·머지):
- 신규 external-social 모듈: SocialIdentityProvider 포트(domain-auth 소유) + Kakao/Naver/Google/Apple OIDC 어댑터. id_token 검증은 검증 라이브러리 위에(D7 — 자작 금지). dev/test는 Mock으로 오프라인 E2E.
- SocialConnection 애그리거트: (provider, providerUserId) 전역 유니크 + (userId, provider) 유니크.
- 소셜 최초 로그인 온보딩: RegistrationSession(SOCIAL) 스텝셋(본인인증 ∧ 필수동의) + 기존 CreateUser 단일 트랜잭션 재사용(AuthAccount + SocialConnection.connect).
- 연동/해제 + LoginMethodPolicy(≥1 로그인 수단 유지). 동시 해제 TOCTOU는 AuthAccount 행 잠금/카운트 제약으로 직렬화 — 동시 해제 2건 중 하나만 성공하는 테스트 포함.
- 애플 특수사항: .p8 동적 client_secret, id_token 검증, 릴레이 이메일(isPrivateRelay)이 contactEmail seed.

검증: 신규 소셜 E2E 가입(Mock), 기존 연동 계정 재로그인, 마지막 수단 해제 거부, 동시 해제 직렬화, 릴레이 이메일 seed, ./gradlew build green.

진행 절차: 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 지적만 반영 → "빌드가 강제하는 불변식" 자기검증 → 커밋·main 머지.

완료 후: todo.md 3번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"의 Phase 2를 [x]로 갱신하면서 확정된 구현 결정·이연 사항을 기록해줘.
```

---

## 4. 이벤트 인프라 — common-messaging·infra-messaging

```text
IMPLEMENTATION_PLAN.md의 D3 이벤트 전달 결정에 따른 이벤트 인프라(common-messaging·infra-messaging)를 구축해줘. Phase 3의 ForceLogoutRequested 소비 등 크로스 도메인 리스너의 전제 작업이다.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§0 D3, §1 모듈 지도의 common-messaging·infra-messaging, §5 이벤트 항목), DOMAIN_MODEL.md(공통 규약 "이벤트", "도메인 이벤트" 표 — 통합 이벤트 목록), docs/architecture.md·docs/coding-conventions.md.

범위:
- common-messaging: MessagePublisher 포트 + 통합 이벤트 공개 스키마 규약(직렬화 가능 + 단조 version + occurredAt) + 멱등 소비 지원(이벤트 ID 기반 디둡) + 내구 재시도(DLQ) 지원.
- infra-messaging: in-process transport 구현(@TransactionalEventListener(AFTER_COMMIT) 발행) + DLQ 테이블(Flyway). 아웃박스 릴레이는 실 브로커/물리 분리 시 도입 — 지금 만들지 않는다(D3).
- 기존 코드가 임시로 처리 중인 이벤트 발행 지점이 있으면 이 포트로 정리(외과적으로 — 무관 리팩터 금지).
- per-userId 순서·멱등 소비 규약을 소비자 계약으로 문서화(코드 강제 가능한 부분은 강제).

검증: 멱등 소비자가 중복 이벤트를 1회만 처리, 소비 실패 시 DLQ 적재 + 재시도 동작, AFTER_COMMIT 발행(롤백 시 미발행), ./gradlew build green.

진행 절차: 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 지적만 반영 → "빌드가 강제하는 불변식" 자기검증 → 커밋·main 머지.

완료 후: todo.md 4번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"에 이벤트 인프라 완료 항목을 추가([x])하면서 확정된 구현 결정·이연 사항을 기록해줘.
```

---

## 5. Phase 3 — 디바이스 + 다중 로그인 제한 + 강제/원격 로그아웃

```text
IMPLEMENTATION_PLAN.md의 Phase 3(디바이스 + 다중 로그인 제한 + 강제/원격 로그아웃)을 진행해줘.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§4 Phase 3, "확정된 구현 결정" 절들), DOMAIN_MODEL.md(§2.4 AccountSessions — createSession maxN·revoke 계열 오퍼레이션, §2.5 Device, 도메인 서비스 표의 ConcurrentSessionPolicy·DeviceRecognitionService, 크로스 플로우 "강제/원격 로그아웃"), REDIS_HA.md(failover revoke 유실·WAIT/WAITAOF 이연 결정), REQUIREMENTS.md(디바이스·인증 절), docs/의 규칙 4문서.

범위(필요 시 슬라이스 분할해 각 슬라이스마다 리뷰·머지):
- Device 애그리거트(auth 스키마): (userId, fingerprint) 유니크, 등록·목록·신뢰/해제·푸시 토큰/권한(pushEnabled).
- 기기 인식: DeviceRecognitionService가 세션 생성 전 지문 매칭, 신규 기기면 DeviceRegistered/NewDeviceDetected 발행(알림 발송 자체는 P5).
- 동시 활성 세션 ≤ N(정책값, 기본 3) 원자 검증 + 초과 시 최오래 세션 축출(ConcurrentLimitExceeded) — Redis Lua 원자 연산, 기존 keyslotting 테스트 규약 유지.
- 내 세션 목록 API(기기·IP·최근 접속) + 특정 세션/현재 제외 전체 원격 로그아웃(revoke·revokeAllExcept).
- 기기 삭제 → revokeByDevice(해당 기기 세션 종료).
- 관리자 강제 종료 훅: ForceLogoutRequested 통합 이벤트 소비 → revokeAll(4번 항목의 이벤트 인프라 사용. 콘솔 UI는 P6).
- REDIS_HA.md 이연분: 강제 로그아웃 소비자와 함께 revoke 계열의 failover 유실 차단(WAIT/WAITAOF) 배선.

검증: N+1 로그인 시 최오래 세션 종료, 원격/강제 종료 후 해당 Access 즉시 401, 기기 삭제 후 그 기기 세션 401, 신규 기기 인식 이벤트 발행, ./gradlew build green.

진행 절차: 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 지적만 반영 → "빌드가 강제하는 불변식" 자기검증 → 커밋·main 머지.

완료 후: todo.md 5번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"의 Phase 3을 [x]로 갱신하면서 확정된 구현 결정·이연 사항을 기록해줘.
```

---

## 6. Phase 4 — 본인인증(실) + 약관·동의 + 탈퇴/보존 + 휴면

```text
IMPLEMENTATION_PLAN.md의 Phase 4(본인인증 실 + 약관·동의 + 탈퇴/보존 + 휴면)를 진행해줘.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§4 Phase 4, §5 보존·파기 및 컴플라이언스 게이트), DOMAIN_MODEL.md(§1.1 User 휴면/탈퇴, §1.2~1.5 본인인증·CI원장·약관·동의, 크로스 플로우 "회원 탈퇴 + 파기"·"상태 스냅샷 동기화", 보존·파기 스케줄), REQUIREMENTS.md(회원·본인인증·약관 절), PROCUREMENT.md(기관 계약 상태), docs/의 규칙 4문서.

범위(슬라이스 분할 권장 — 실 본인인증 / 약관·동의 / 탈퇴·쿨다운 / 휴면·배치 순, 각 슬라이스마다 리뷰·머지):
- external-identity 실 어댑터(계약된 기관) 배선, 포트 계약 유지 — Mock↔실 피처플래그 스위치가 도메인 무변경임을 검증.
- 약관 유형·버전 관리(publishVersion, (type,version) 유니크·불변) + 필수 새 버전 발행 시 재동의 유발(ReConsentRequired) + 이용 게이트(ConsentPolicy).
- 동의/철회(선택 약관만 철회 허용) + ConsentState fold 읽기모델 + 마케팅 동의 ↔ NotificationPreference 동기화.
- 탈퇴: withdraw → WITHDRAWN + PII 즉시 파기(법정 보존 제외) + CiRegistry tombstone + UserWithdrawn 통합 이벤트로 인증이 세션 revokeAll·자격증명/소셜/기기 정리. 신규 로그인 창 닫기: 탈퇴가 인증-로컬 userStatus를 동기 플립하거나 로그인/세션생성이 pending-withdrawal에 fail-closed.
- 재가입 쿨다운: tombstone withdrawnAt + cooldown(30일) 기준 거부·잔여일 반환.
- 휴면: 12개월 미접속 전환 + 30일 전 사전통지 + LastLoginObserved 이벤트로 lastLoginAt 갱신. 해제는 유저 서비스 권위의 OTP 재인증 강제(스냅샷 ACTIVE여도 통과 금지 — DORMANT는 안전 비대칭 아님).
- 신규 app-batch 모듈: 휴면 전환 잡 + 보존 파기 잡(RetentionPolicy 외부화, append-only 3종은 PII만 crypto-shred/가명화).

검증: Mock↔실 스위치 도메인 무변경, 필수 새 버전 발행 시 재동의 요구, 필수 약관 철회 거부, 탈퇴 직후 신규 로그인 거부 + 기존 세션 전멸 + 평문 PII 잔존 없음, 쿨다운 내 재가입 거부, 스냅샷 지연 상황에서도 DORMANT는 OTP 없이 로그인 불가, 배치 경계값 전환·파기, ./gradlew build green.

진행 절차: 슬라이스마다 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 지적만 반영 → "빌드가 강제하는 불변식" 자기검증 → 커밋·main 머지.

완료 후: todo.md 6번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"의 Phase 4를 [x]로 갱신하면서 확정된 구현 결정·이연 사항을 기록해줘. prod 전 법무 최종검토·PIA 판정 게이트가 남아 있음을 명시해줘.
```

---

## 7. Phase 5 — 이상탐지·알림 + 레이트리밋 + 계정 잠금

```text
IMPLEMENTATION_PLAN.md의 Phase 5(이상탐지·알림 + 레이트리밋 + 계정 잠금)를 진행해줘.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§4 Phase 5), DOMAIN_MODEL.md(§1.6 NotificationPreference, §2.6 LoginAttempt·RiskEvaluator, §3.1 Notification·NotificationDispatchPolicy, 도메인 서비스 표의 AccountLockPolicy·RateLimitPolicy), REQUIREMENTS.md(이상탐지·알림, 보안·레이트리밋 절), docs/의 규칙 4문서.

범위(슬라이스 분할 권장, 각 슬라이스마다 리뷰·머지):
- 신규 domain-generic 모듈(generic 스키마): Notification 애그리거트(PENDING→SENT/FAILED, retryCount).
- external-notification 실 어댑터(SMTP/SES·SMS·FCM/APNs) — 기존 Mock 포트 유지, 피처플래그 스위치.
- GeoIpLookup 포트 + 신규 기기/신규 지역 감지 → RiskEvaluator가 riskScore 산출·LoginAttempt에 기록.
- 발송 판정(NotificationDispatchPolicy): 유저 NotificationPreference AND (PUSH면 Device.pushEnabled ∧ pushToken) AND 카테고리. 수신자 주소는 유저 contactEmail/contactPhone만(loginEmail 미사용).
- 보안 카테고리 강제 발송(opt-out 무시, 최소 EMAIL/SMS) — DOMAIN_MODEL §3.1의 강제 이벤트 열거 구독.
- 알림 수신 설정 API(채널×카테고리 opt-in, SECURITY 최소 채널 해제 거부).
- 레이트리밋: 민감 엔드포인트 IP/계정 기준 + 인증코드 일일 발송 한도 → 초과 시 429.
- 연속 실패 잠금: Redis 카운터+TTL → 임계 초과 시 AuthAccount.lock(TEMP), 쿨다운 경과 자동 해제.
- 발송 실패 재시도·격리(DLQ — 4번 항목 인프라 사용).

검증: 신규 지역 로그인 riskScore 상승, opt-out 반영 + 채널 AND, 보안 알림이 수신거부에도 발송, SECURITY 최소 채널 해제 거부, 임계 초과 429, N회 실패 후 TEMP_LOCKED + 쿨다운 후 해제, Mock 실패 주입 시 재시도, ./gradlew build green.

진행 절차: 슬라이스마다 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 지적만 반영 → "빌드가 강제하는 불변식" 자기검증 → 커밋·main 머지.

완료 후: todo.md 7번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"의 Phase 5를 [x]로 갱신하면서 확정된 구현 결정·이연 사항을 기록해줘.
```

---

## 8. Phase 6 — 관리자 콘솔 + RBAC + 감사

```text
IMPLEMENTATION_PLAN.md의 Phase 6(관리자 콘솔 + RBAC + 감사)을 진행해줘.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§4 Phase 6), DOMAIN_MODEL.md(§1.7 Role/Permission/UserRole, §2.1 실효 회원상태 읽기모델, §3.2 AuditLog, 크로스 플로우 "강제/원격 로그아웃"), REQUIREMENTS.md(권한·관리자, 감사 절), docs/architecture.md(infrastructure/query 격리 구역 규칙), docs/의 나머지 규칙 문서.

범위(슬라이스 분할 권장, 각 슬라이스마다 리뷰·머지):
- RBAC: Role/Permission/UserRole(usr 스키마, 최초 가입 시 USER 배정) — 배정은 유저, 집행은 인증(토큰 클레임). RoleChanged 통합 이벤트 → 인증이 클레임 갱신.
- 신규 app-admin 모듈: 회원 검색(전화 blind index 활용)/상세(마스킹 정책 적용), 강제 로그아웃(ForceLogoutRequested 발행), 계정 잠금/해제(ADMIN_LOCKED), 로그인 이력 조회, 권한 변경. 관리자 API는 역할·권한 기반 접근 통제.
- infrastructure/query 격리 구역의 크로스스키마 read(읽기 전용, 도메인 경계 무침범).
- AuditLog 정식화(generic 스키마): append-only·WORM(update/delete 차단), 관리자 행위·권한 변경은 전/후 값 기록, 인증 이벤트(로그인/로그아웃/재발급/강제 로그아웃) 기록, 조회 제공. 보존 2년 후 PII crypto-shred는 기존 app-batch 잡에 연결.
- 실효 회원상태 읽기모델: effective = f(lifecycle, lock), 우선순위 WITHDRAWN > LOCKED > DORMANT > ACTIVE.

검증: 비관리자의 관리자 API 접근 거부, 검색 결과 마스킹, 강제 로그아웃 즉시 반영(기존 Access 401), AuditLog update/delete 차단 + 전/후 값 기록, 합성 상태 렌더, ./gradlew build green.

진행 절차: 슬라이스마다 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 지적만 반영 → "빌드가 강제하는 불변식" 자기검증 → 커밋·main 머지.

완료 후: todo.md 8번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"의 Phase 6을 [x]로 갱신하면서 확정된 구현 결정·이연 사항을 기록해줘.
```

---

## 9. Phase 1a 이연 하드닝

```text
Phase 1a에서 의도적으로 이연한 운영 하드닝 항목들을 해소해줘.

먼저 읽을 것: IMPLEMENTATION_PLAN.md("진행 현황"의 Phase 1a 하위 항목별 "결정·이연의 귀결" 메모 전부, "Phase 1a 확정된 구현 결정"), REDIS_HA.md, docs/의 규칙 4문서.

범위(항목별로 독립 슬라이스 가능, 각각 리뷰·머지):
- JWKS 자동 90일 회전: 회전 트리거 배선(주의 — @Scheduled에 "90d"는 부팅 실패, ISO "P90D" 또는 ms만 허용) + 내구 키스토어/시크릿매니저 주입 + 다중 인스턴스 키·JWKS 공유. 이 셋은 함께 도입해야 재시작 시 401·다중 인스턴스 상호 검증 실패가 해소된다. grace ≥ 2×Access TTL 불변식은 기존 빈 조립 fail-fast 유지.
- revokeAll 단일 Lua 원자화 + Lettuce 연결단 타임아웃(connectTimeout/disconnectedBehavior) — REDIS_HA.md 후속 항목.
- PasswordCredential change/resetTo의 KDF를 로그인처럼 트랜잭션 밖으로 이동(리미터 포화 시 커넥션 점유 완화 백로그).
- 조건부: spring.threads.virtual.enabled 활성화 계획이 있으면 Argon2 세마포어를 전용 플랫폼-스레드 실행기로 교체(코드 주석의 트리거 조건 참고). 활성화 계획이 없으면 건드리지 않는다.

검증: 재시작 후 직전 발급 Access가 grace 창 내 유효, 다중 인스턴스(또는 이를 시뮬레이션한 두 컨텍스트) 상호 토큰 검증, 회전 시 기존 키 grace 검증 유지, revokeAll 원자성, ./gradlew build green.

진행 절차: 항목마다 설계 → 콜드 서브에이전트 설계 리뷰 → 구현 → 콜드 서브에이전트 코드 리뷰 → 타당한 지적만 반영 → "빌드가 강제하는 불변식" 자기검증 → 커밋·main 머지.

완료 후: todo.md 9번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"의 Phase 1a 하위 "운영 수용"·"이연의 귀결" 메모들을 해소됨으로 갱신해줘.
```

---

## 10. 운영·보안 게이트 (prod 배포 전 비협상)

```text
IMPLEMENTATION_PLAN.md §5의 횡단 관심사 중 아직 미배선인 운영·보안 게이트를 점검·구축해줘. prod 배포 전 비협상 항목이다.

먼저 읽을 것: IMPLEMENTATION_PLAN.md(§0.1 비용 드라이버, §5 전체, §6 리스크), REDIS_HA.md, REQUIREMENTS.md(비기능 요구사항), PROCUREMENT.md(법무 게이트 상태), docs/code-quality.md.

범위(성격이 달라 항목별 독립 진행 권장 — 코드 작업과 문서/파이프라인 작업을 구분):
- 성능·용량: 세션 핫패스·Lua 경합 부하 테스트, Redis 메모리 모델(세션당×동시×상한) 실측, Argon2 비용 예산 검증(로그인 폭주 시 메모리·지연 안정).
- 관측성·SLO: 로그인 p99·가용성 SLO 정의, 보안 탐지 지표(재사용 감지 급증·잠금율·회전 이상) 대시보드·알럿, 구조적 로깅·헬스체크.
- CI/CD·롤아웃: 파이프라인 + 테스트 게이트 + 이미지 빌드 + app-migration init 컨테이너 실행, Flyway expand-contract 규약 명문화(PII 컬럼 드롭 롤백 불가), Mock↔실 피처플래그·카나리.
- 시크릿 관리: JWT 서명키·DB·소셜 시크릿·애플 .p8·기관 사이트코드·ciHash pepper·PII KEK의 저장/주입/회전 절차. pepper 유출 = CI dedup 비연결성 파괴(보안 크리티컬).
- DR/HA: PostgreSQL 백업/PITR, Redis 지속성, RTO/RPO 명시, KMS/복호화 불가 시 런북.
- 보안 테스트: 위협모델(STRIDE), 펜테스트 준비, SCA(의존성 스캔) 파이프라인 편입, enumeration 저항(로그인/가입 에러 패리티)·timing-safe 비교 점검.
- 컴플라이언스: 법무 형식 최종검토 + PIA 필요성 판정을 명문 게이트로(PROCUREMENT.md와 연결).
- API 계약: 공개 OpenAPI + 계약 테스트 + presentation/v{n} 버저닝.

진행 방식: 먼저 위 항목별 현재 상태(완료/부분/미착수)를 레포에서 실사해 갭 목록을 만들고, 코드로 해소 가능한 갭부터 슬라이스로 진행(각각 설계 → 콜드 리뷰 → 구현 → 콜드 코드 리뷰 → 반영 → 커밋·main 머지). 조직/외부 의존 항목(펜테스트·법무)은 오너·기한을 문서화한다.

완료 후: todo.md 10번 항목을 [x]로 체크하고, IMPLEMENTATION_PLAN.md "진행 현황"에 운영·보안 게이트 결과(통과 항목·잔여 외부 의존)를 기록해줘.
```
