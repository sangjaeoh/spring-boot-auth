# 인증·유저 도메인 모델 (Domain Model)

이 문서는 인증 서비스의 도메인 모델을 소유한다 — 어떤 애그리거트가 있고, 각 애그리거트가 어떤 **엔티티·값객체·필드·상태·정책·불변식·오퍼레이션**을 갖는지. 즉 "실제로 무엇을 만드는가"를 필드 수준까지 고정한다.

- 요구사항(*무엇을*)은 [`REQUIREMENTS.md`](./REQUIREMENTS.md)가 소유한다.
- 이 문서는 **자립 문서**다: 엔티티·필드·상태·정책과 함께, 비-자명한 선택의 *근거*는 [핵심 설계 결정](#핵심-설계-결정-왜-이렇게)이, 튜닝 가능한 값은 [비즈니스 정책값](#비즈니스-정책값-설정)이, 보존/파기의 법적 근거는 [보존·파기 스케줄](#보존파기-스케줄)이 담는다.
- 코드/패키지 구조·ORM 매핑·Redis 키 스키마·Flyway 스크립트·배포 토폴로지·API 스펙은 이 문서의 범위가 아니다(구현 규약·구현 계획이 소유). 이 문서는 그중 **모델링을 좌우하는 지점만** 한 줄로 표기한다.

범위는 3개 서비스·17개 애그리거트: **유저 서비스**(회원 · 본인인증 · CI원장 · 약관 · 동의 · 알림설정 · 권한) · **인증 서비스**(인증계정 · 비밀번호 · 소셜 · 세션 · 디바이스 · 로그인이력 · 인증코드 · 온보딩) · **제네릭**(알림 · 감사).

기준선(이 문서의 전제): **세션 = Redis 진실원본**(강제·다중 로그인 제한을 실시간 반영하는 의도적 stateful) + **이력/감사 = RDB**. JWT(Access) + 서버 세션 저장소. 외부 연동(본인확인기관·소셜 IdP·알림 발송·GeoIP)은 **포트(ACL)로 격리**하고 dev는 Mock. **인증/유저 서비스는 언제든 물리 분리 가능**하도록 DB·애그리거트를 공유하지 않고 ID 참조 + 계약/이벤트로만 연결한다. MFA는 범위 밖 — 자리(슬롯)만 확보한다.

---

## 공통 규약

- **식별자**: 엔티티 PK는 앱에서 생성하는 UUIDv7(`@GeneratedValue` 없음). 예외로 자연키가 식별에 필수인 곳은 서러게이트 PK + 유니크 제약으로 강제한다(`ci_hash`, `(provider, provider_user_id)`, `(terms_type, version)`).
- **UserId = 공유 정체성 계약**: `UserId`(UUID)는 **유저 서비스가 발급·소유**하는 캐노니컬 사람 식별자다. 인증·제네릭은 이를 **참조만** 한다. 두 서비스가 공유하는 것은 `UserId` + **최소 회원 상태 스냅샷**(`ACTIVE`/`DORMANT`/`WITHDRAWN`) + 통합 이벤트 스키마뿐이며, 그 외 내부 모델은 비공개다.
- **서비스 경계·크로스 참조**: 애그리거트는 서비스 경계(seam)를 넘지 않는다. 타 서비스·타 애그리거트 개념은 **순수 `UUID xxxId` 값**으로만 참조한다(물리 FK·객체 연관·크로스 서비스 JOIN 금지). 같은 애그리거트 내부만 객체 연관을 둔다.
- **저장소 이원화**: **RDB(JPA)** = 회원·계정·이력·감사 등 내구·질의 대상. **Redis** = 세션(진실원본)·원타임 코드·온보딩 사가처럼 **TTL·원자성·핫패스**가 핵심인 휘발 상태. Redis 애그리거트는 테이블이 아니라 키·TTL 구조라 아래 표에서 "저장: Redis"로 표기하고 스키마/테이블을 두지 않는다.
- **시각**: `createdAt`·`updatedAt`은 JPA Auditing이 채운다(엔티티가 직접 선언하지 않음, RDB 한정). 업무 시각(`verifiedAt`·`changedAt`·`revokedAt` 등)은 도메인 메서드가 명시적으로 세팅한다.
- **상태**: enum(문자열 저장). 상태 변경은 **의도 동사 메서드 + 허용 전이 가드**로만 한다(setter 없음). 실세계 전이·정책으로 정당화되는 상태만 두고, 아무도 전이시키지 않는 죽은 상태는 피한다. 파생 가능한 값(만료·소진·실효상태)은 상태 컬럼으로 굳히지 않고 조회 시 파생한다.
- **개인정보 암호화**: 평문 PII(이름·생년월일·성별·휴대폰·DI)는 **컬럼 암호화(AES-GCM/KMS)** 저장, 최소 수집. **CI는 원문을 저장하지 않는다** — `ciHash`(salted HMAC + pepper)만 보관한다. 관리자 조회 출력은 마스킹 정책을 적용한다.
- **삭제·파기·보존**: 단순 소프트삭제(`deletedAt`)가 아니라 **생명주기 상태(WITHDRAWN) + 보존기간별 파기**를 쓴다. 탈퇴 시 PII는 즉시 파기(법정 보존분만 분리보관), CI는 유한 tombstone 보존 후 파기한다. 보존/파기 스케줄은 [보존·파기 스케줄](#보존파기-스케줄) 절이 소유하며 `RetentionPolicy` 공용 설정으로 외부화한다(하드코딩 금지).
- **append-only 이력**: `ConsentRecord`·`LoginAttempt`·`AuditLog`는 **insert 전용**(update/delete 금지). 보존기간 경과 시 레코드·순서·해시체인은 유지한 채 **PII 값만 crypto-shred/가명화**한다(append 사실은 불변, PII 값만 소거).
- **원자성·동시성**: 다중 로그인 상한·리프레시 회전 등 경합 지점은 **Redis 원자 연산**(Lua/트랜잭션)으로 직렬화한다. RDB 낙관락(`@Version`)은 기본으로 두지 않고 실 경합이 있는 곳에만 둔다. 연속 실패 잠금 카운터는 **Redis 카운터+TTL**로 이력과 분리한다.
- **이벤트**: **도메인 이벤트**(서비스 내부)와 **통합 이벤트**(seam 계약, 서비스 간)를 구분한다. 통합 이벤트는 직렬화 가능한 공개 스키마 + 단조 version + `occurredAt`을 갖고, 트랜잭셔널 아웃박스로 유실 없이 발행한다. 아래 표기에서 **굵은 이벤트**는 통합 이벤트다.
- **표기**: 각 애그리거트는 필드 표(모든 RDB 엔티티는 `id`·`createdAt`·`updatedAt`을 갖는다)·상태 표·정책·불변식·오퍼레이션 표(연산·입력·강제 불변식·거부)로 기술한다. 반환 형상·예외 매핑·서비스 역할 배치·네이밍·동시성 메커니즘은 **구현 규약**(추후 `docs/`)이 소유한다.

## 공용 값 객체

여러 도메인이 공유하는 값 객체다. 도메인 전용 VO(`Profile`·`VerificationResult`·`RefreshToken` 등)는 각 도메인 절에 둔다.

### Email

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| value | String | 필수 | 정규화(소문자·trim) 저장. 형식 검증(VO). 단일 컬럼 매핑 |

- 불변 값. 정규화는 생성 시 강제한다. 인증의 **로그인 식별자**(`LoginEmail`)와 유저의 **연락용 주소**(`contactEmail`)는 같은 `Email` 타입을 쓰되 **소유·의미가 다른 별개 개념**이다(아래 각 절).

### PhoneNumber

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| carrier | Carrier | 필수 | 통신사(SKT·KT·LGU·MVNO). 본인인증 결과 |
| number | String | 필수 | E.164 정규화. **암호화 저장**(PII) |

- 본인인증으로만 채워지는 PII. 다중 컬럼 `@Embeddable`.

---

## 비즈니스 정책값 (설정)

튜닝 가능한 정책 수치를 한곳에 모은다. 전부 `application.yml`·`RetentionPolicy` 등 **설정으로 외부화**하며 하드코딩하지 않는다.

| 정책값 | 값 | 소유 | 비고 |
|---|---|---|---|
| 동시 활성 세션 상한 | **3** (확정) | 인증 | 초과 시 가장 오래된 세션 축출 |
| 로그인 연속 실패 잠금 임계 | 5회 (설정) | 인증 | 초과 시 `TEMP_LOCKED` |
| 일시잠금 쿨다운 | 30분 (설정) | 인증 | 경과 시 자동 해제 |
| Access 토큰 수명 | 15분 (설정) | 인증 | 짧은 수명 |
| Refresh 토큰 수명 | 14일 (설정) | 인증 | 회전 대상 |
| Refresh 재사용 유예 창 | 10초 (설정) | 인증 | 정상 재시도 허용, 밖이면 탈취 간주 |
| 비밀번호 재사용 금지 개수 N | 최근 5개 (설정) | 인증 | `PasswordHistory` |
| 인증코드 TTL | 5분 (설정) | 인증 | `VerificationChallenge` |
| 인증코드 시도 상한 | 5회 (설정) | 인증 | |
| 인증코드 일일 발송 한도 | 채널별 (설정) | 인증 | SMS/이메일 |
| 온보딩 세션 TTL | 30~60분 (확정) | 인증 | 만료 시 자동 파기 |
| 휴면 전환 미접속 기간 | **12개월** (확정) | 유저 | 서비스 정책(법적 의무 아님) |
| 휴면 사전통지 | **30일 전** (확정) | 유저 | 파기/분리보관 고지 |
| 재가입 쿨다운 | **30일** (확정) | 유저 | 동일 CI/이메일/전화 |
| CI tombstone 보존 | **6개월** (확정) | 유저 | 유한 보존 후 완전 파기 |
| 로그인이력·IP 보존 | **2년** (확정) | 인증 | IP는 90일 후 가명화 |
| 감사로그 보존 | **2년** (확정) | 제네릭 | WORM |
| 동의이력 보존 | 회원유지 + 탈퇴 후 **5년** (확정) | 유저 | append-only |
| JWKS 키 회전 | 90일 (확정) | 인증 | grace ≥ 2×Access TTL |

> `(확정)` = 법령/표준 근거 있는 고정값([핵심 설계 결정](#핵심-설계-결정-왜-이렇게)·[보존·파기 스케줄](#보존파기-스케줄)). `(설정)` = 운영 조정 기본값 — 위 수치는 **권장 초기값(미확정)**이며 실측·정책으로 확정한다.

---

## 1. 유저 서비스 (User Service)

사람(=회원)의 정체성·프로필·생명주기·개인정보·동의·권한을 소유한다. 스키마 `usr`(USER 예약어 회피 — [용어집](#도메인-용어집-예약어-divergence)).

### 1.1 회원 (User) — 애그리거트 루트

회원의 캐노니컬 식별을 소유한다. 로그인 식별자·자격증명은 **두지 않는다**(인증 서비스 소유). PII는 본인인증 결과로만 채운다.

- 애그리거트 루트: `User`
- 스키마/테이블: `usr` / `users`

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7. **UserId** — 사람의 캐노니컬 식별자, 두 서비스 공유 계약 |
| profile | Profile(VO) | 필수 | 이름·생년월일·성별. 본인인증 결과로만 채움. **암호화** |
| contact | Contact(VO) | 필수 | 연락용 이메일·휴대폰. **알림 수신자 진실원본** |
| status | LifecycleStatus | 필수 | 아래 상태표(생명주기 축) |
| ciHash | String | 필수 | CI 원장 참조(연계 키). 원문 CI 미저장 |
| lastLoginAt | Instant | 선택 | 휴면 판정용. 인증의 `LastLoginObserved` 이벤트로 갱신(세션 직접조회 금지) |
| dormantAt | Instant | 선택 | 휴면 전환 시각(DORMANT에서만 존재) |
| dormancyNotifiedAt | Instant | 선택 | 휴면 30일 전 사전통지 발송 시각 |
| withdrawnAt | Instant | 선택 | 탈퇴 시각(WITHDRAWN에서만 존재) |
| createdAt | Instant | 필수 | 생성 시각(Auditing) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing) |

- **역할 배정**(`roleId[]`)은 User 컬럼이 아니라 `UserRole` 조인(§1.7)으로 둔다 — 배정 이력·다대다라 별 테이블이 자연스럽다.
- 탈퇴를 `deletedAt`이 아니라 `status = WITHDRAWN` + PII 파기로 표현하는 이유: 탈퇴는 단순 숨김이 아니라 **PII 파기·CI tombstone·재가입 정책**을 수반하는 생명주기 종결이라 소프트삭제 규약과 다르다.

#### 값 객체 (Profile)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| name | String | 필수 | 실명. **암호화**. 본인인증 결과 |
| birthDate | LocalDate | 필수 | 생년월일. **암호화**. 본인인증 결과 |
| gender | Gender | 필수 | MALE·FEMALE. 본인인증 결과 |

#### 값 객체 (Contact)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| contactEmail | Email(VO) | 필수 | 알림·연락용 주소. 가입 시 인증 `loginEmail`로 seed 후 **독립 변경 가능**(소셜 릴레이 이메일 대응) |
| contactPhone | PhoneNumber(VO) | 필수 | 연락·SMS 수신용. 본인인증 결과로 seed |

#### 상태 (LifecycleStatus)

| 상태 | 의미 |
|---|---|
| ACTIVE | 활성. 정상 이용 자격 |
| DORMANT | 휴면. 장기 미접속으로 분리보관, 재인증(OTP)으로 해제 |
| WITHDRAWN | 탈퇴. PII 파기·CI tombstone 완료(종료 상태) |

- 전이: `ACTIVE → DORMANT`(`makeDormant`), `DORMANT → ACTIVE`(`reactivate`, 재인증 후), `ACTIVE → WITHDRAWN`·`DORMANT → WITHDRAWN`(`withdraw`). 최초 상태 ACTIVE(온보딩 사전조건 충족 후에만 생성). WITHDRAWN은 종료.
- **보안 잠금은 이 축에 없다** — 잠금(연속 실패·관리자)은 인증 서비스 `AuthAccount.LockState`(§2.1)가 소유하는 직교 오버레이다. 사용자·관리자 표시용 "활성/휴면/잠금/탈퇴"는 두 축을 합성한 **실효 상태 읽기모델**(§2.1)로 파생한다(한 컬럼에 겹치지 않는다).

#### 정책·불변식

- **생성(register, 팩토리)**: **본인인증 완료 ∧ 필수 약관 최신 버전 동의** 후에만 ACTIVE로 생성된다. 한 사람(=하나의 CI)당 활성 User는 하나 — `CiRegistry`(§1.3) 유니크가 보장한다.
- **PII 응집·최소수집**: 이름·생년·성별·휴대폰은 본인인증 결과로만 채워지며 암호화 저장한다. 유저 경계 밖으로 원문을 복사하지 않는다.
- **contactEmail ≠ loginEmail**: 연락용 `contactEmail`(유저 소유)은 로그인 식별자 `loginEmail`(인증 소유)과 별개다. 이메일·SMS 알림 수신자 주소는 **유저 서비스가 유일 진실원본**이며 알림은 인증 `loginEmail`을 참조하지 않는다.
- **휴면 정책**: 1년(12개월) 미접속 시 DORMANT 전환, **30일 전 사전통지**(파기/분리보관 사실·만료일·대상항목), 해제는 로그인 후 재인증(OTP), 휴면 데이터는 분리보관. `lastLoginAt`은 인증이 이벤트로 통지한다(유저가 세션 저장소를 직접 조회하지 않는다). 법적 의무 아닌 서비스 정책이라 기간은 설정값.
- **탈퇴·재가입**: `withdraw`는 PII를 즉시 파기(법정 보존항목만 분리보관)하고 `CiRegistry`를 tombstone 전이한다. 재가입은 **이력 승계 없는 새 계정**이며 동일 CI/이메일/전화 기준 **쿨다운(기본 30일)**을 적용한다. 세션·자격증명·기기 정리는 `UserWithdrawn` 통합 이벤트로 인증이 수행한다(연쇄 정리 비동기).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| register | profile(본인인증분), contactEmail, ciHash, consents | 사전조건(본인인증·필수동의), CI 유일, 최초 ACTIVE | 사전조건 미충족; CI 중복(활성); 재가입 쿨다운 |
| updateProfile | userId, newProfile | 프로필 갱신(암호화) | 미존재 |
| changeContactEmail | userId, newEmail | `contactEmail` 갱신(`loginEmail` 불변·독립) | 미존재; 형식 오류 |
| makeDormant | userId | ACTIVE→DORMANT, `dormantAt` 세팅 | 미존재; 잘못된 전이 |
| reactivate | userId | DORMANT→ACTIVE(재인증 선행), `dormantAt` clear | 미존재; 잘못된 전이; 재인증 미완 |
| withdraw | userId, reason | →WITHDRAWN, PII 파기, `withdrawnAt` 세팅, CI tombstone | 미존재; 이미 탈퇴 |
| getUser | userId | 활성 회원 1행(마스킹 정책 적용 조회 별도) | 미존재 |

발행 이벤트: `UserRegistered`, `UserProfileUpdated`, **`UserStatusChanged`**, **`UserWithdrawn`**.

### 1.2 본인인증 (IdentityVerification) — 애그리거트 루트

외부 본인확인기관 실명확인의 요청·결과를 소유한다. 결과(CI/DI 포함)는 암호화 저장하고 보존기간 경과 시 파기한다.

- 애그리거트 루트: `IdentityVerification`
- 스키마/테이블: `usr` / `identity_verification`
- 포트: `IdentityVerificationProvider`(외부 기관). dev는 Mock.

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7. **VerificationId** — 온보딩이 참조하는 키 |
| userId | UUID | 선택 | 연결된 회원(완료·CreateUser 시 채움. 진행 중엔 null) |
| provider | Provider | 필수 | NICE·KG·DANAL. 실명확인 기관 |
| status | VerificationStatus | 필수 | 아래 상태표 |
| result | VerificationResult(VO) | 선택 | 완료 시 결과(암호화). VERIFIED에서만 존재 |
| requestedAt | Instant | 필수 | 요청 시각 |
| verifiedAt | Instant | 선택 | 완료 시각(VERIFIED 세팅) |
| expiresAt | Instant | 필수 | 진행 만료 시각(EXPIRED 파생 기준) |
| createdAt | Instant | 필수 | 생성 시각(Auditing) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing) |

#### 값 객체 (VerificationResult)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| name | String | 필수 | 실명. **암호화** |
| birthDate | LocalDate | 필수 | 생년월일. **암호화** |
| gender | Gender | 필수 | MALE·FEMALE |
| carrier | Carrier | 필수 | 통신사 |
| phone | String | 필수 | 휴대폰. **암호화** |
| ciHash | String | 필수 | CI의 salted HMAC(원문 미저장). 중복가입 방지 키 |
| di | String | 필수 | 사이트별 식별정보. **암호화** |

#### 상태 (VerificationStatus)

| 상태 | 의미 |
|---|---|
| REQUESTED | 인증 요청됨. 기관 응답 대기 |
| VERIFIED | 실명확인 성공. 결과 확보 |
| FAILED | 인증 실패 |
| EXPIRED | 진행 만료(미완) |

- 전이: `REQUESTED → VERIFIED`(`complete`), `REQUESTED → FAILED`(`fail`), `REQUESTED → EXPIRED`(TTL 경과 파생/스윕). 최초 REQUESTED. VERIFIED/FAILED/EXPIRED는 종료.

#### 정책·불변식

- **완료 불변**: `complete(result)` 후 결과는 불변이다. `ciHash`는 원문 CI가 아니라 salted HMAC로만 보관한다.
- **암호화·보존**: `VerificationResult`의 PII는 암호화 저장하고, **목적 달성 즉시 최소화**한다(회원 유지 최소분만) — 불필요분은 지체 없이 파기([보존표](#보존파기-스케줄)).
- **Mock 대체**: dev/test는 외부 호출 없이 동작하는 Mock 어댑터로 동작한다. 포트는 유저 컨텍스트에 귀속되며 온보딩 사가는 이를 직접 호출하지 않고 유저를 경유한다.
- **CI soft-check**: 완료 시점엔 `CiRegistry` **읽기 조회**로 중복/재가입만 조기 판정(hard-enforce는 `CreateUser` 트랜잭션의 유니크 인덱스가 담당 — TOCTOU 회피, §1.3·크로스 플로우).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| request | provider, target | 최초 REQUESTED, `expiresAt` 세팅 | 레이트리밋 초과 |
| complete | verificationId, result | REQUESTED→VERIFIED, 결과 불변, `verifiedAt` 세팅 | 미존재; 잘못된 전이 |
| fail | verificationId, reason | REQUESTED→FAILED | 미존재; 잘못된 전이 |

발행 이벤트: `IdentityVerificationRequested`, **`IdentityVerified`**(verificationId, ciHash).

### 1.3 CI 원장 (CiRegistry) — 애그리거트 루트

`ciHash` 유일성 원장이다. 동일인 다중가입을 차단하고, 탈퇴 후에도 부정재가입 방지 목적으로 tombstone을 유한 보존한다.

- 애그리거트 루트: `CiRegistry`
- 스키마/테이블: `usr` / `ci_registry`

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| ciHash | String | 필수 | salted HMAC(+pepper). **전역 유니크**. 원문 CI 미저장 |
| status | CiStatus | 필수 | 아래 상태표 |
| linkedUserId | UUID | 선택 | 연결된 회원. ACTIVE_LINKED에서만 존재 |
| firstSeenAt | Instant | 필수 | 최초 등록 시각 |
| withdrawnAt | Instant | 선택 | 탈퇴 tombstone 전이 시각. 보존창(파기 기준) |
| createdAt | Instant | 필수 | 생성 시각(Auditing) |
| updatedAt | Instant | 필수 | 수정 시각(Auditing) |

#### 상태 (CiStatus)

| 상태 | 의미 |
|---|---|
| ACTIVE_LINKED | 활성 회원에 연결됨(`linkedUserId` 존재). 신규 가입 차단 |
| WITHDRAWN_RETAINED | 탈퇴 tombstone(`linkedUserId = null`). 유한 보존 후 파기 |

- 전이: `(신규) → ACTIVE_LINKED`(`link`), `ACTIVE_LINKED → WITHDRAWN_RETAINED`(`retainOnWithdrawal`). 보존창 경과 시 레코드 자체 파기(원장에서 제거).

#### 정책·불변식

- **전역 유일**: `ciHash`는 유니크 인덱스로 hard-enforce한다 → 동일인 다중 활성가입 봉쇄. `link(userId)`는 **`CreateUser` 단일 트랜잭션 안에서** 수행되어(userId가 그 트랜잭션서 채번) `linkedUserId` 불변식이 처음부터 유효하다.
- **tombstone 유한 보존**: 탈퇴 시 `retainOnWithdrawal`로 tombstone 전이하고, **부정재가입 방지 보존창(기본 6개월, 쿨다운 30일 포함) 경과 후 완전 파기**한다(무기한 영구차단 미채택 — 별도 법무 근거 시만). 해시 단일 항목·분리보관·처리방침 고지·탈퇴 전 동의 4요건을 충족한다.
- **재가입 판정**: `CiUniquenessService`가 가입 시 조회로 판정한다 — ACTIVE_LINKED면 중복(차단), WITHDRAWN_RETAINED면 `withdrawnAt + cooldown` 기준으로 재가입 허용/거부/잔여일 반환(인증엔 결과만 노출, tombstone 내부 비공개).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| checkUniqueness | ciHash | 조회로 중복/재가입 판정(read) | — |
| link | ciHash, userId | 유니크 hard-enforce, →ACTIVE_LINKED | ciHash 중복(활성); 재가입 쿨다운 |
| retainOnWithdrawal | userId | ACTIVE_LINKED→WITHDRAWN_RETAINED, `linkedUserId` clear, `withdrawnAt` 세팅 | 미존재 |

발행 이벤트: `CiLinked`, `CiRetainedOnWithdrawal`(도메인 이벤트).

### 1.4 약관 (TermsDocument / TermsVersion) — 두 애그리거트 루트

약관을 유형·버전으로 관리한다. 문서(유형)와 버전은 생명주기가 달라(버전은 append·불변) 별 애그리거트다.

- 애그리거트 루트: `TermsDocument`(유형), `TermsVersion`(버전)
- 스키마/테이블: `usr` / `terms_document`, `terms_version`

#### TermsDocument 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| type | TermsType | 필수 | **유니크**. SERVICE·PRIVACY_REQUIRED·PRIVACY_OPTIONAL·AGE14·MARKETING·THIRD_PARTY |
| required | boolean | 필수 | 필수 동의 여부(가입 전제 판정용) |
| createdAt / updatedAt | Instant | 필수 | Auditing |

#### TermsVersion 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| type | TermsType | 필수 | 소속 약관 유형 참조 |
| version | int | 필수 | `(type, version)` **유니크**. 단조 증가 |
| content | String | 필수 | 약관 원문(또는 원문 참조) |
| effectiveFrom | Instant | 필수 | 발효 시각 |
| createdAt / updatedAt | Instant | 필수 | Auditing |

#### 정책·불변식

- **버전 불변·append**: 버전은 발행 후 불변이며 새 내용은 새 버전으로만 추가한다. 동의는 항상 특정 `(type, version)`을 참조한다.
- **재동의 유발**: 필수 약관 새 버전 발행 시 기존 사용자는 **재동의 필요** 상태가 된다(`ReConsentRequired`). 이용 게이트가 최신 필수버전 동의를 검증한다(§1.5 `ConsentPolicy`).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| registerType | type, required | `type` 유니크 | 유형 중복 |
| publishVersion | type, content, effectiveFrom | `(type, version)` 유니크, version = 직전+1, 불변 | 미존재 유형; 버전 충돌 |

발행 이벤트: `TermsVersionPublished`, `ReConsentRequired`(도메인 이벤트).

### 1.5 동의 이력 (ConsentRecord) + 읽기모델 (ConsentState)

동의·철회를 **append-only로 영구 보관**한다(언제 무엇에 동의/철회했는지 — 법적 요구). 현재 동의 스냅샷은 읽기모델로 파생한다.

- 애그리거트 루트: `ConsentRecord`(append 로그), 파생 읽기모델: `ConsentState`
- 스키마/테이블: `usr` / `consent_record`, `consent_state`

#### ConsentRecord 필드 (append-only)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| userId | UUID | 필수 | 회원 참조 |
| termsType | TermsType | 필수 | 대상 약관 유형 |
| termsVersion | int | 필수 | 대상 약관 버전 |
| action | ConsentAction | 필수 | AGREE·WITHDRAW |
| channel | MarketingChannel | 선택 | 마케팅 채널(EMAIL·SMS·PUSH). MARKETING에만 |
| at | Instant | 필수 | 동의/철회 시각(도메인 세팅) |
| createdAt | Instant | 필수 | 적재 시각(Auditing) |

#### ConsentState 필드 (읽기모델, fold 캐시)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| userId | UUID | 필수 | `(userId, termsType)` 유니크 |
| termsType | TermsType | 필수 | 약관 유형 |
| currentAction | ConsentAction | 필수 | 현재 유효 동의 상태 |
| agreedVersion | int | 선택 | 현재 동의한 버전 |
| updatedAt | Instant | 필수 | 마지막 반영 시각 |

#### 정책·불변식

- **append-only·영구보존**: `ConsentRecord`는 insert 전용·불변이다. update/delete 없음. `ConsentState`는 로그를 fold해 재구축 가능한 캐시라 append-only 위배가 아니다.
- **철회 범위**: `WITHDRAW`는 **선택** 약관에만 허용한다. 필수 약관 철회는 이용 불가 → 탈퇴 경로로만 처리한다.
- **온보딩 버퍼링**: 가입 최초 동의는 인증 `RegistrationSession`에 버퍼링되었다가 `CreateUser` 단일 트랜잭션에서 `userId`와 함께 append된다(**userId 없는 선-기록 금지**). 이용 중 동의/철회는 유저에 직접 기록.
- **마케팅 동기화**: `ConsentGiven/Withdrawn(MARKETING)`는 `NotificationPreference`(§1.6) 마케팅 매트릭스를 동기화한다.

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| agree | userId, termsType, version, channel? | AGREE append, `ConsentState` 갱신 | 미존재 버전 |
| withdraw | userId, termsType | WITHDRAW append(선택 약관만), `ConsentState` 갱신 | 필수 약관 철회 시도 |
| getConsentState | userId | 현재 동의 스냅샷 | — |

발행 이벤트: **`ConsentGiven`**, **`ConsentWithdrawn`**.

### 1.6 알림 수신 설정 (NotificationPreference) — 애그리거트 루트

채널×카테고리 수신 동의(opt-in) 매트릭스를 소유한다. 실제 발송 판정은 제네릭 알림(§3.1)이 이 설정과 기기 권한을 AND 한다.

- 애그리거트 루트: `NotificationPreference`
- 스키마/테이블: `usr` / `notification_preference`(+ `notification_preference_entry`)

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| userId | UUID | 필수 | PK(회원당 1개). 회원 참조 |
| entries | Set\<PreferenceEntry\> | 필수 | 채널×카테고리 opt-in 매트릭스(자식) |
| createdAt / updatedAt | Instant | 필수 | Auditing |

#### 값 객체/자식 (PreferenceEntry)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| channel | NotificationChannel | 필수 | EMAIL·SMS·PUSH |
| category | NotificationCategory | 필수 | SECURITY·ACCOUNT·MARKETING. `(channel, category)` 유니크 |
| allowed | boolean | 필수 | 수신 허용 |

#### 정책·불변식

- **보안 강제**: `SECURITY` 카테고리는 **수신거부 불가**(최소 EMAIL 또는 SMS 강제). opt-out을 무시한다.
- **마케팅 일치**: `MARKETING` 허용은 마케팅 약관 동의와 일치해야 한다(`syncMarketingFromConsent`가 `ConsentGiven/Withdrawn(MARKETING)`을 반영).
- **소유·집행 분리**: 이 설정은 유저 소유(진실원본)다. **실제 발송 판정**은 제네릭 `NotificationDispatchPolicy`가 이 설정 + `Device` 푸시토큰/권한(인증)을 AND 하여 수행한다.

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| allow | userId, channel, category | 해당 엔트리 allowed=true | 미존재 |
| disallow | userId, channel, category | allowed=false(SECURITY는 최소 채널 유지 강제) | 보안 최소 채널 해제 시도 |
| syncMarketingFromConsent | userId, agreed | 마케팅 엔트리 = 마케팅 동의 | — |

발행 이벤트: **`NotificationPreferenceChanged`**.

### 1.7 권한 (Role / Permission) — RBAC

역할·권한과 사용자–역할 배정을 소유한다. **배정은 유저**, **집행은 인증/게이트웨이**(토큰 클레임)다.

- 애그리거트 루트: `Role`, `Permission`
- 스키마/테이블: `usr` / `role`, `permission`, `role_permission`, `user_role`

#### Role 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| name | RoleName | 필수 | **유니크**. USER·ADMIN·SUPER_ADMIN |
| permissionIds | Set\<UUID\> | 선택 | `role_permission` 조인으로 매핑 |
| createdAt / updatedAt | Instant | 필수 | Auditing |

#### Permission 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| resource | String | 필수 | 대상 리소스 |
| action | String | 필수 | 행위(read·write·…). `(resource, action)` 유니크 |
| createdAt / updatedAt | Instant | 필수 | Auditing |

#### UserRole 필드 (배정)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| userId | UUID | 필수 | 회원 참조. `(userId, roleId)` 유니크 |
| roleId | UUID | 필수 | 역할 참조 |
| assignedAt | Instant | 필수 | 배정 시각 |

#### 정책·불변식

- 역할–권한 매핑은 `role_permission`, 사용자–역할 배정은 `user_role`로 둔다. 최초 가입 시 USER 역할이 배정된다.
- **집행 위치**: 관리자 API 접근통제는 인증/게이트웨이가 **토큰 클레임 기반**으로 집행한다. 역할 변경은 `RoleChanged` 통합 이벤트로 인증에 전파되어 토큰 클레임을 갱신한다.

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| assignRole | userId, roleId | `(userId, roleId)` 유니크 | 중복 배정 |
| revokeRole | userId, roleId | 배정 제거 | 미배정 |
| grantPermission | roleId, permissionId | 역할–권한 매핑 추가 | 미존재 |

발행 이벤트: **`RoleChanged`**.

---

## 2. 인증 서비스 (Auth Service)

로그인·자격증명·세션·기기·보안(잠금/위험/레이트리밋)을 소유한다. 매 로그인/매 요청마다 유저 서비스를 호출하지 않고 자체 데이터로 동작한다. 스키마 `auth`.

### 2.1 인증 계정 (AuthAccount) — 애그리거트 루트

특정 User의 로그인 식별자·보안 상태·인증수단 묶음을 소유한다. `UserId`로 회원과 1:1 연결된다.

- 애그리거트 루트: `AuthAccount`
- 스키마/테이블: `auth` / `auth_account`

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| userId | UUID | 필수 | PK(= UserId, 회원과 1:1). 인증계정 식별자 |
| loginEmail | Email(VO) | 필수 | **로그인 식별자**. 정규화 후 **유니크**. 연락용 `contactEmail`과 별개 |
| lockState | LockState | 필수 | 아래 상태표(보안 잠금 오버레이) |
| lockedAt | Instant | 선택 | 잠금 시각(lockState ≠ NONE에서 존재) |
| lockReason | LockReason | 선택 | 잠금 사유(TEMP_LOCKED·ADMIN_LOCKED 구분 컨텍스트) |
| userStatus | LifecycleStatus | 필수 | **유저 생명주기의 읽기전용 투영 스냅샷**. 유저=유일 writer, 이벤트로 동기화 |
| userStatusVersion | long | 필수 | 스냅샷 단조 version(순서 역전 방지) |
| createdAt / updatedAt | Instant | 필수 | Auditing |

- `authAccountId`는 별도 서러게이트를 두지 않고 `userId`를 PK로 쓴다(회원과 1:1). 인증수단(`PasswordCredential`·`SocialConnection`)은 별 루트이며 `userId`로 참조한다.

#### 값 객체 (LoginEmail)

- 공용 `Email` VO를 로그인 식별자 의미로 쓴다. 정규화(소문자·trim) + 전역 유니크. 변경은 `changeLoginEmail`.

#### 상태 (LockState — 보안 잠금 오버레이)

| 상태 | 의미 |
|---|---|
| NONE | 잠금 없음 |
| TEMP_LOCKED | 연속 로그인 실패 임계 초과 일시잠금(쿨다운 경과 시 해제) |
| ADMIN_LOCKED | 관리자 잠금(관리자 해제 필요) |

- 전이: `NONE → TEMP_LOCKED`(연속 실패 N회), `TEMP_LOCKED → NONE`(쿨다운/해제), `NONE → ADMIN_LOCKED`, `ADMIN_LOCKED → NONE`(관리자 해제). **생명주기(`userStatus`)와 직교**한다.

#### 정책·불변식

- **로그인 이메일 유일**: `loginEmail`은 정규화 후 활성 계정 사이 유니크.
- **최소 1개 로그인 수단 유지**: `PasswordCredential` 또는 `SocialConnection` 중 **≥1**을 항상 유지한다. 마지막 수단 제거는 거부(`LoginMethodPolicy`).
- **로그인 핫패스 접근 판정(인증-로컬)**: `허용 = userStatus(로컬 스냅샷) == ACTIVE AND lockState == NONE`. **매 로그인마다 유저 서비스를 동기 호출하지 않는다.** 탈퇴는 `UserWithdrawn`가 세션·자격증명을 정리하므로 스냅샷 지연 시에도 로그인이 자연 차단된다(안전 비대칭).
- **`userStatus`는 읽기전용 투영**: 유저가 유일 writer이고 인증은 `UserStatusChanged`/`UserWithdrawn`(단조 version·per-userId 순서·멱등 소비)로만 갱신한다. 인증이 status를 직접 write하지 않는다.
- **실효 회원상태 읽기모델**: 관리자/내계정 표시는 생명주기(유저)와 잠금(인증) 두 축을 **합성**해 `effective = f(lifecycle, lock)`(우선순위 `WITHDRAWN > LOCKED > DORMANT > ACTIVE`)로 파생, "활성/휴면/잠금/탈퇴"를 렌더한다(두 축을 한 컬럼에 병합하지 않는다).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| create | userId, loginEmail | `loginEmail` 유니크, 최초 lockState NONE·userStatus ACTIVE | 이메일 중복 |
| changeLoginEmail | userId, newEmail | `loginEmail` 유니크 갱신(`contactEmail` 무관) | 미존재; 이메일 중복 |
| lock | userId, reason(TEMP·ADMIN) | NONE→TEMP/ADMIN_LOCKED, `lockedAt` 세팅 | 미존재; 잘못된 전이 |
| unlock | userId, byAuthority | LOCKED→NONE(권한 검증), `lockedAt` clear | 미존재; 권한 부족 |
| attachFactor | userId, factorRef | 인증수단 추가 | 미존재 |
| detachFactor | userId, factorRef | ≥1 수단 유지 검증 | 마지막 수단 제거 |
| applyUserStatus | userId, status, version | version > 현재일 때만 스냅샷 갱신 | 역순 이벤트(무시) |

발행 이벤트: **`AccountLocked`**, **`AccountUnlocked`**, `LoginEmailChanged`.

### 2.2 비밀번호 자격증명 (PasswordCredential) — 애그리거트 루트

로컬 로그인 자격증명을 소유한다. 안전 해시로만 저장하고 정책·재사용 금지를 강제한다.

- 애그리거트 루트: `PasswordCredential`(계정당 0..1)
- 스키마/테이블: `auth` / `password_credential`, `password_history`

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| userId | UUID | 필수 | PK(계정당 0..1). 인증계정 참조 |
| passwordHash | String | 필수 | **Argon2id/BCrypt** 해시(원문·복호화 불가) |
| algorithm | HashAlgorithm | 필수 | ARGON2ID·BCRYPT(마이그레이션 대비) |
| changedAt | Instant | 필수 | 마지막 변경 시각 |
| history | List\<PasswordHistoryEntry\> | 필수 | 최근 N개 해시(재사용 금지 검증용) |
| resetToken | ResetToken(VO) | 선택 | 재설정 진행 중 토큰(TTL). 완료·만료 시 clear |
| createdAt / updatedAt | Instant | 필수 | Auditing |

#### 값 객체 (PasswordHistoryEntry / ResetToken)

| VO | 필드 | 제약·설명 |
|---|---|---|
| PasswordHistoryEntry | hash, changedAt | 최근 N개만 보관(오래된 것 축출) |
| ResetToken | tokenHash, expiresAt | 재설정 토큰 해시 + 만료. 원문 미저장 |

#### 정책·불변식

- **안전 해시**: Argon2id(또는 BCrypt)로만 저장한다. 원문·가역 암호 금지.
- **정책·재사용 금지**: 변경 시 길이·복잡도(`PasswordPolicy`)와 **최근 N개 재사용 금지**(`PasswordHistory` 대조)를 강제한다.
- **재설정**: 메일/토큰 기반 재설정(`initiateReset`→`completeReset`) 및 로그인 상태 변경을 제공한다. `resetToken`은 해시·TTL로만 보관한다.
- **패밀리**: 추상 `AuthenticationFactor`의 형제 — `PasswordCredential`·`SocialConnection`·(향후)`TotpCredential`. → [MFA 슬롯](#mfa-확장-슬롯).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| set | userId, rawPassword | 정책 충족, 해시 저장, 최초 이력 적재 | 정책 위반 |
| verify | userId, raw | 해시 대조(부작용 없음) | — |
| change | userId, newRaw | 정책 + 최근 N 재사용 금지, `changedAt`·이력 갱신 | 정책 위반; 재사용 |
| initiateReset | userId | `resetToken`(해시·TTL) 발급 | 미존재 |
| completeReset | resetToken, newRaw | 토큰 유효·미만료, 정책·이력 검증, `resetToken` clear | 토큰 무효·만료; 정책 위반 |

발행 이벤트: **`PasswordChanged`**, `PasswordResetRequested`, `PasswordResetCompleted`.

### 2.3 소셜 연동 (SocialConnection) — 애그리거트 루트

카카오·네이버·구글·애플 OAuth2/OIDC 연합 수단을 소유한다.

- 애그리거트 루트: `SocialConnection`
- 스키마/테이블: `auth` / `social_connection`
- 포트: `SocialIdentityProvider`(id_token 검증). dev는 Mock/샌드박스.

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| userId | UUID | 필수 | 인증계정 참조. `(userId, provider)` 유니크(계정당 provider 1개) |
| provider | SocialProvider | 필수 | KAKAO·NAVER·GOOGLE·APPLE |
| providerUserId | String | 필수 | IdP subject. `(provider, providerUserId)` **유니크** |
| providerEmail | Email(VO) | 선택 | IdP 제공 이메일 |
| isPrivateRelay | boolean | 필수 | 애플 릴레이 이메일 여부(기본 false) |
| linkedAt | Instant | 필수 | 연동 시각 |
| createdAt / updatedAt | Instant | 필수 | Auditing |

#### 정책·불변식

- **subject 유일**: `(provider, providerUserId)`는 전역 유니크 — 한 소셜 계정은 하나의 인증계정에만 연결된다. 계정당 한 provider는 1개(`(userId, provider)` 유니크).
- **≥1 수단 유지**: `disconnect`는 `LoginMethodPolicy`로 **최소 1개 로그인 수단 유지**를 검증한다.
- **애플 특수**: 동적 `client_secret`(.p8 서명)·`id_token` 검증·최초 1회 정보 제공·릴레이 이메일(`isPrivateRelay`)을 처리한다.
- **최초 로그인 온보딩**: 기존 연동이 없으면 `RegistrationSession(SOCIAL)`로 약관동의·본인인증 온보딩을 유도한다(크로스 플로우 §온보딩).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| connect | userId, provider, providerUserId, email? | `(provider, providerUserId)`·`(userId, provider)` 유니크 | 이미 타 계정 연결; 중복 provider |
| disconnect | userId, provider | ≥1 로그인 수단 유지 검증 후 제거 | 마지막 수단 제거 |

발행 이벤트: **`SocialConnected`**, **`SocialDisconnected`**.

### 2.4 계정 세션 집합 (AccountSessions) — 애그리거트 루트  *(동시 세션 정합 경계)*

사용자-기기 단위 로그인 상태를 소유한다. **Redis가 진실원본**이며 동시 활성 세션 상한을 원자적으로 보장한다.

- 애그리거트 루트: `AccountSessions`(userId 단위 세션 집합), 자식 엔티티 `Session`, VO `RefreshToken`
- 저장: **Redis(진실원본)**. 이력은 이벤트로 RDB(`session_history`).

#### Session 구조 (Redis 엔티티)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| sessionId | UUID | 필수 | 세션 식별자. Access JWT의 `sid` 클레임 |
| userId | UUID | 필수 | 계정 참조 |
| deviceId | UUID | 필수 | 바인딩된 기기 |
| status | SessionStatus | 필수 | ACTIVE·REVOKED·EXPIRED |
| refreshToken | RefreshToken(VO) | 필수 | 현재 리프레시(회전 대상) |
| issuedAt | Instant | 필수 | 발급 시각(축출 우선순위 기준) |
| lastAccessedAt | Instant | 필수 | 최근 접속 시각 |
| expiresAt | Instant | 필수 | 만료 시각(TTL) |
| ip | String | 필수 | 발급 IP |
| ua | String | 선택 | User-Agent |

#### 값 객체 (RefreshToken)

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| jtiHash | String | 필수 | 현재 리프레시 jti 해시(원문 미저장) |
| status | RefreshStatus | 필수 | ACTIVE·ROTATED·REVOKED |
| prevJtiHash | String | 선택 | 직전 토큰(회전 계보·유예 창 판정) |
| issuedAt | Instant | 필수 | 발급 시각 |

- **세션 = 리프레시 토큰 패밀리 경계**다. 회전 계보가 세션에 귀속된다.

#### 상태 (SessionStatus)

| 상태 | 의미 |
|---|---|
| ACTIVE | 유효 세션 |
| REVOKED | 무효화됨(로그아웃·강제종료·재사용 감지) |
| EXPIRED | TTL 만료 |

- 전이: `ACTIVE → REVOKED`(revoke 계열), `ACTIVE → EXPIRED`(TTL). 무효화는 **즉시 반영**.

#### 정책·불변식

- **동시 활성 세션 ≤ N**(정책값, 예: 3): `createSession`이 maxN을 **원자 검증 + 초과 시 가장 오래된 세션 자동 축출**한다.
- **즉시 무효화**: `session:{id}` **O(1)** 유효성 검증으로 매 요청 세션을 확인한다. 강제 로그아웃된 세션의 토큰은 유효기간이 남아도 즉시 거부된다.
- **리프레시 회전·재사용 탐지**: `rotateRefresh`는 원자 회전한다. **직전 토큰 유예 창** 내 재시도는 정상으로 허용(동일 신규 토큰 반환), 유예 밖 사용/폐기 토큰 재사용은 **탈취로 간주**해 `revokeAll`(세션 패밀리 전체 무효화) + 사용자 알림.
- **핫패스 분리**: 세션 O(1) 검증 읽기모델은 애그리거트 로드와 분리한다.

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| createSession | userId, deviceId, ip, ua | maxN 원자 검증 + 최오래 축출, ACTIVE 생성 | — |
| validate | sessionId | O(1) 유효성(ACTIVE·미만료) | 무효·만료 세션 |
| rotateRefresh | sessionId, presentedJti | 원자 회전; 유예 창 내 재시도 허용 | 무효 세션 |
| detectReuse | sessionId, presentedJti | 유예 밖 재사용 시 `revokeAll` | — |
| revoke | sessionId | ACTIVE→REVOKED | 미존재 |
| revokeAllExcept | userId, currentSessionId | 현재 제외 전체 무효화 | — |
| revokeAll | userId | 전체 무효화(강제 종료) | — |
| revokeByDevice | deviceId | 해당 기기 세션 종료 | — |

발행 이벤트: `SessionCreated`, **`SessionRevoked`**, **`ConcurrentLimitExceeded`**, `RefreshRotated`, **`RefreshReuseDetected`**.

### 2.5 디바이스 (Device) — 애그리거트 루트

로그인 기기를 등록·식별하고 **푸시 타겟**(토큰·OS 권한)을 소유한다.

- 애그리거트 루트: `Device`
- 스키마/테이블: `auth` / `device`

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7. **DeviceId** |
| userId | UUID | 필수 | 계정 참조. `(userId, fingerprint)` 유니크 |
| deviceName | String | 필수 | 표시 기기명 |
| platform | DevicePlatform | 필수 | iOS·ANDROID·WEB 등 |
| fingerprint | String | 필수 | 기기 지문(재식별 키) |
| lastIp | String | 선택 | 최근 접속 IP |
| lastAccessedAt | Instant | 선택 | 최근 접속 시각 |
| trusted | boolean | 필수 | 신뢰 기기 여부(기본 false) |
| pushToken | String | 선택 | 푸시 토큰 |
| pushPlatform | PushPlatform | 선택 | FCM·APNs |
| pushEnabled | boolean | 필수 | OS 푸시 권한 허용(기본 false) |
| createdAt / updatedAt | Instant | 필수 | Auditing |

#### 정책·불변식

- **기기 인식(세션 생성 전)**: `DeviceRecognitionService`가 지문으로 기존 기기를 매칭한다(세션 생성 **전**). 신규 기기면 `DeviceRegistered`/`NewDeviceDetected` → 알림.
- **삭제 → 세션 종료**: 기기 삭제 시 해당 기기 세션을 종료한다(`revokeByDevice` 교차 이벤트).
- **푸시 타겟**: 발송 판정 시 제네릭 알림이 `pushEnabled ∧ pushToken` 존재를 조회한다(수신설정은 유저, 기기 권한은 인증 — AND).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| register | userId, deviceName, platform, fingerprint | `(userId, fingerprint)` 유니크 | 중복 등록 |
| trust / untrust | deviceId | `trusted` 토글 | 미존재 |
| updatePushToken | deviceId, token, pushPlatform | 푸시 토큰 갱신 | 미존재 |
| setPushEnabled | deviceId, enabled | OS 권한 반영 | 미존재 |
| delete | deviceId | 삭제 → `revokeByDevice`(세션 종료) | 미존재 |

발행 이벤트: **`DeviceRegistered`**, `DeviceTrusted`/`DeviceUntrusted`, `PushTokenRegistered`, `PushPermissionChanged`, `DeviceDeleted`.

### 2.6 로그인 이력 (LoginAttempt) — 애그리거트 루트  *(append 로그)*

로그인 성공/실패를 시도별 불변 기록으로 남긴다. 잠금 카운터와는 분리한다.

- 애그리거트 루트: `LoginAttempt`(append-only)
- 스키마/테이블: `auth` / `login_attempt`

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| userId | UUID | 선택 | 계정(식별 실패 시 null) |
| result | LoginResult | 필수 | SUCCESS·FAILURE |
| failureReason | FailureReason | 선택 | 실패 사유(BAD_CREDENTIAL·LOCKED·…) |
| ip | String | 필수 | 요청 IP. 90일 후 가명화(보존표) |
| deviceId | UUID | 선택 | 기기 |
| riskScore | int | 필수 | 위험도. 신규 기기/지역/실패 이력 기반 |
| at | Instant | 필수 | 시도 시각 |
| createdAt | Instant | 필수 | 적재 시각(Auditing) |

#### 정책·불변식

- **불변 기록**: insert 전용. 보존창(2년) 경과 후 파기, IP는 90일 후 가명화(append 사실·순서 불변, PII만 소거).
- **잠금 카운터 분리**: 연속 실패 **잠금 카운터는 Redis(TTL)** — `AccountLockPolicy`가 관리하며 이력(RDB)과 분리한다. 임계 초과 시 `AuthAccount.lock(TEMP)`.
- **위험도**: `RiskEvaluator`(inline)가 신규 기기/지역/실패 이력으로 `riskScore`를 산출해 기록하고, 임계 시 알림/스텝업(MFA 슬롯)을 트리거한다.

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| record | userId?, result, ip, deviceId?, riskScore | append(불변) | — |

발행 이벤트: **`LoggedIn`**, `LoginFailed`, (파생) `NewDeviceDetected`/`NewLocationDetected`.

### 2.7 원타임 인증코드 (VerificationChallenge) — 애그리거트 루트  *(TTL)*

이메일/SMS/비밀번호 재설정 코드를 소유한다. TTL·시도 상한·일일 한도를 강제한다.

- 애그리거트 루트: `VerificationChallenge`
- 저장: **Redis(TTL)**

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | ChallengeId |
| type | ChallengeType | 필수 | EMAIL·SMS·PASSWORD_RESET(+MFA 예약) |
| target | String | 필수 | 수신 대상(이메일/전화). 마스킹 조회 |
| codeHash | String | 필수 | 코드 해시(원문 미저장) |
| expiresAt | Instant | 필수 | 만료(TTL) |
| attempts | int | 필수 | 시도 횟수(기본 0) |
| maxAttempts | int | 필수 | 시도 상한 |

#### 정책·불변식

- **TTL·시도 상한·쿨다운**: 코드는 TTL 내·시도 상한 이하에서만 유효하다. 재발송 쿨다운을 둔다.
- **일일 발송 한도**: SMS/이메일 발송 일일 한도를 적용한다. `issue`가 `RateLimitPolicy`·일일한도를 검증한다.
- 코드 전달은 `NotificationSender` 포트로(dev Mock).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| issue | type, target | 레이트리밋·일일한도 검증, TTL·maxAttempts 세팅 | 한도 초과; 쿨다운 |
| verify | challengeId, code | 코드 일치 ∧ 미만료 ∧ attempts < max, attempts++ | 불일치; 만료; 시도 초과 |

발행 이벤트: `VerificationCodeIssued`, `VerificationCodeVerified`/`VerificationCodeFailed`(도메인 이벤트).

### 2.8 가입·온보딩 사가 (RegistrationSession) — 애그리거트 루트  *(TTL)*

정회원 생성 전 가입/소셜 온보딩의 임시 진행 상태를 소유한다. **영속 PENDING 계정을 만들지 않는다.**

- 애그리거트 루트: `RegistrationSession`
- 저장: **Redis(TTL 30~60분)**

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | RegistrationId(멱등키) |
| registrationType | RegistrationType | 필수 | LOCAL·SOCIAL |
| steps | StepSet(VO) | 필수 | 스텝 완료 플래그(아래) |
| verificationRef | UUID | 선택 | `VerificationId` 참조(**PII 원문 미보유**) |
| bufferedConsents | List\<ConsentInput\> | 필수 | 동의 선택값 버퍼(userId 없이) |
| provisionalContext | ProvisionalContext(VO) | 선택 | 소셜 subject·loginEmail 등 임시 컨텍스트 |
| onboardingToken | String | 필수 | 온보딩 전용 토큰(정식 Access/Refresh 아님) |
| expiresAt | Instant | 필수 | 만료(TTL). 만료 시 자동 파기 |

#### 값 객체 (StepSet)

| 필드 | 타입 | 제약·설명 |
|---|---|---|
| emailVerified | boolean | LOCAL 필수 |
| phoneVerified | boolean | LOCAL 필수 |
| identityVerified | boolean | 공통 필수 |
| requiredConsented | boolean | 공통 필수 |

#### 상태·완료 판정

- 완료 조건은 **가입유형별 필수 스텝셋**으로 판정한다(고정 논리곱 아님):
  - `LOCAL` = 이메일인증 ∧ 휴대폰인증 ∧ 본인인증 ∧ 필수동의
  - `SOCIAL` = 본인인증 ∧ 필수동의 (이메일=IdP 검증으로, 휴대폰=본인인증 결과로 충족)
- 진행: `RegistrationStarted` → (스텝 마킹) → `RegistrationCompleted`(충족) 또는 `RegistrationAbandoned`(TTL 만료).

#### 정책·불변식

- **영속 PENDING 금지**: 충족 전 정회원 미생성. 온보딩 전용 토큰만 발급한다(좀비 계정 방지).
- **PII 원문 미보유**: 본인인증 결과는 원문을 복사하지 않고 `verificationRef`(암호화·짧은 TTL)만 든다. 동의 선택값만 버퍼링한다. PII는 유저 경계 안에 가둔다.
- **굵은 명령 1회(크로스서비스 사가)**: `complete()`는 유저에 **단일 `CreateUser(verificationRef, ciHash, consents[], registrationType)`** 명령을 보낸다(유저 애그리거트를 seam 너머로 개별 조작하지 않는다). 멱등키 = `RegistrationId`. 상세는 [크로스 플로우](#크로스-서비스-정책플로우).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| startLocal | loginEmail | 최초 LOCAL, 온보딩 토큰 발급 | — |
| startSocial | provider, subject | 최초 SOCIAL, provisionalContext 세팅 | — |
| markStep | registrationId, step | 스텝 플래그 세팅 | 만료 |
| complete | registrationId | 유형별 스텝셋 충족 검증 → `CreateUser` | 스텝 미충족; 만료 |

발행 이벤트: `RegistrationStarted`, **`RegistrationCompleted`**, `RegistrationAbandoned`.

---

## 3. 제네릭 서비스 (Generic Service)

양 서비스 이벤트를 구독하는 알림·감사를 소유한다. 독립 서비스로도 배포 가능. 스키마 `generic`.

### 3.1 알림 (Notification) — 애그리거트 루트

이메일·SMS·**푸시** 발송 이력·상태를 소유한다. 발송 판정은 유저 수신설정 + 기기 권한 + 카테고리 정책을 AND 한다.

- 애그리거트 루트: `Notification`
- 스키마/테이블: `generic` / `notification`
- 포트: `NotificationSender`(EMAIL/SMS/PUSH). dev=Mock(콘솔/DB).

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| userId | UUID | 필수 | 수신 회원 참조 |
| category | NotificationCategory | 필수 | SECURITY·ACCOUNT·MARKETING |
| channel | NotificationChannel | 필수 | EMAIL·SMS·PUSH |
| templateId | String | 필수 | 템플릿 |
| payload | String(JSON) | 필수 | 렌더 데이터 |
| status | NotificationStatus | 필수 | PENDING·SENT·FAILED |
| retryCount | int | 필수 | 재시도 횟수(기본 0) |
| failureReason | String | 선택 | FAILED 사유 |
| sentAt | Instant | 선택 | 발송 시각 |
| createdAt / updatedAt | Instant | 필수 | Auditing |

#### 상태 (NotificationStatus)

| 상태 | 의미 |
|---|---|
| PENDING | 발송 대기 |
| SENT | 발송 완료 |
| FAILED | 발송 실패(재시도 대상) |

#### 정책·불변식

- **발송 판정(`NotificationDispatchPolicy`)**: `발송 = NotificationPreference(유저) AND (PUSH면 Device.pushEnabled ∧ pushToken 존재, 인증) AND 카테고리 정책`.
- **수신자 주소 = 유저**: 이메일/SMS 수신자는 유저 `contactEmail`/`contactPhone`에서만 조회한다(인증 `loginEmail` **미사용**).
- **보안 강제**: `SECURITY` 카테고리는 마케팅 opt-out을 무시하고 최소 EMAIL(또는 SMS)로 강제 발송한다. 강제 이벤트 열거: `RefreshReuseDetected`·`PasswordChanged`/`Reset*`·`AccountLocked`·`NewDeviceDetected`·`ConcurrentLimitExceeded`·`LoginEmailChanged`·`UserWithdrawn`.
- **실패 격리·재시도**: 발송 실패 시 재시도·격리(NFR).
- **구독**: `NewDeviceDetected`·`RefreshReuseDetected`·`PasswordChanged`·`ConcurrentLimitExceeded`·`AccountLocked`·`UserWithdrawn` 등.

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| dispatch | userId, category, channel, templateId, payload | 정책 판정(수신설정·기기권한·카테고리), 발송 | 수신거부(보안 제외); 기기 권한 없음(PUSH) |
| retry | notificationId | FAILED 재발송, `retryCount++` | 미존재; FAILED 아님 |

발행 이벤트: `NotificationSent`, `NotificationFailed`(도메인 이벤트).

### 3.2 감사 로그 (AuditLog) — 애그리거트 루트  *(append-only, immutable)*

인증/관리 이벤트를 수정 불가하게 기록한다.

- 애그리거트 루트: `AuditLog`(append-only)
- 스키마/테이블: `generic` / `audit_log`(WORM 지향)

#### 필드

| 필드 | 타입 | 필수 | 제약·설명 |
|---|---|---|---|
| id | UUID | 필수 | PK, UUIDv7 |
| actor | String | 필수 | 행위 주체(userId·adminId·SYSTEM) |
| action | String | 필수 | 행위(로그인·강제로그아웃·권한변경 등) |
| target | String | 선택 | 대상 식별자 |
| before | String(JSON) | 선택 | 변경 전 값(관리자 행위·권한 변경) |
| after | String(JSON) | 선택 | 변경 후 값 |
| context | String(JSON) | 선택 | ip 등 컨텍스트 |
| at | Instant | 필수 | 발생 시각 |
| createdAt | Instant | 필수 | 적재 시각(Auditing) |

#### 정책·불변식

- **수정·삭제 불가**: append only. update/delete 금지. 보존창(2년) 후 PII crypto-shred(레코드·순서 불변).
- **전/후 값**: 관리자 행위·권한 변경은 `before`/`after`를 함께 기록한다. 인증 이벤트 기록.
- **유실 없는 캡처**: 통합 이벤트를 트랜잭셔널 아웃박스로 유실 없이 감사에 append한다(구현은 범위 밖 — 도메인은 "이벤트→감사 append"만 규정).

#### 오퍼레이션

| 연산 | 입력 | 강제 불변식 | 거부 |
|---|---|---|---|
| record | actor, action, target?, before?, after?, context? | append(불변) | — |

---

## 도메인 서비스·정책

애그리거트 하나에 담기 어려운 **교차 규칙·핫패스 로직**이다. 소유 서비스를 함께 표기한다.

| 정책/서비스 | 소유 | 핵심 로직 | 관련 FR |
|---|---|---|---|
| `ConcurrentSessionPolicy` | 인증 | `AccountSessions` maxN 원자 검증 + 최오래 세션 축출 | 8.1·8.2 |
| `RefreshRotation + ReuseDetection` | 인증 | 원자 회전; 직전 토큰 유예 창 허용, 유예 밖 재사용은 세션 전체 무효화 | 5.3·5.4 |
| `AccountLockPolicy` | 인증 | 연속 실패 N회(Redis 카운터+TTL) 초과 시 `lock(TEMP)`; 관리자 잠금 `ADMIN_LOCKED` | 12.2 |
| `PasswordPolicy` | 인증 | 길이·복잡도 + 최근 N개 재사용 금지 | 10.2 |
| `LoginMethodPolicy` | 인증 | 소셜 해제/비번 제거 시 ≥1 로그인 수단 유지(교차 애그리거트 read-check) | 6.3 |
| `RiskEvaluator` | 인증 | inline. 신규 기기/지역/실패 이력 → `riskScore` 산출·기록, 임계 시 알림/스텝업 | 11.1·11.3 |
| `DeviceRecognitionService` | 인증 | 지문→기존 기기 매칭(세션 생성 전); 신규 시 `NewDeviceDetected` | 7.1·11.1 |
| `RateLimitPolicy` | 인증(엣지) | 민감 엔드포인트 IP/계정 제한 + 코드 일일 한도(필터/인터셉터 수준) | 12.1·12.3 |
| `CiUniquenessService` | 유저 | `CiRegistry` 조회로 중복(ACTIVE_LINKED=차단)/재가입(tombstone=쿨다운 판정) | 2.3·16.3 |
| `ConsentPolicy` | 유저 | 필수 약관 최신 버전 동의 충족 검증(가입·이용 중) | 1.4·3.x |
| `RetentionPolicy + PurgeJob` | 유저(+각 서비스) | 보존기간별 파기(즉시/기한/유한); 탈퇴 시 PII 파기 + tombstone. 데이터 소유=파기 소유 | 16.2·2.4 |
| `NotificationDispatchPolicy` | 제네릭 | 수신자 주소·수신설정=유저 AND 기기 푸시토큰·권한=인증 AND 카테고리; 보안은 opt-out 무시 | 11.2 |

---

## 크로스 서비스 정책·플로우

여러 서비스를 잇는 흐름의 정책이다. 크로스BC라 분산 ACID가 불가능해, **굵은 명령(단일 트랜잭션) + 전진복구(forward recovery) + 멱등키**로 정합을 맞춘다. 각 서비스는 자기 트랜잭션만 소유한다.

### 회원가입 온보딩 (LOCAL, 크로스서비스 사가)

인증이 주도한다. 전진 경로:

1. **가입 시작**: 인증이 `RegistrationSession(LOCAL)` 생성 + 온보딩 토큰. `VerificationChallenge(EMAIL)` 발송.
2. **이메일/휴대폰 인증**: 코드 검증 → `markStep(emailVerified)`, 이어 `VerificationChallenge(SMS)` → `markStep(phoneVerified)`.
3. **본인인증**: 인증이 유저에 본인인증 수행을 요청 → 유저가 `IdentityVerificationProvider`로 실명확인 → `IdentityVerification` 저장(VerificationId 키) + **CI soft-check**(중복/재가입 조기 판정) → `IdentityVerified(verificationId, ciHash)` → 인증이 `markStep(identityVerified)` + `verificationRef` 보관.
4. **필수 동의**: `markStep(requiredConsented)` + 동의값 **버퍼링**(유저에 write 안 함).
5. **완료(굵은 명령)**: 스텝셋 충족 시 `complete()` → 유저에 **단일 `CreateUser(verificationRef, ciHash, consents[], LOCAL)`**. 유저가 **한 트랜잭션**에서 UserId 채번 → `User(ACTIVE)` → `ConsentRecord(userId)` append → `CiRegistry.link(userId, ciHash)`(유니크 hard-enforce) → `IdentityVerification` 연결을 원자 수행하고 `UserId` 반환.
6. **계정 생성**: 인증이 그 UserId로 `AuthAccount(userStatus=ACTIVE)` + `PasswordCredential`을 생성. 유저가 `UserRegistered` 발행 → 환영/보안 알림.

- **정합성**: `CreateUser`(먼저)/`AuthAccount`(나중) 순서 + 전진복구(멱등키=`RegistrationId`, CI 유니크 제약이 이중생성 backstop). 잔여 고아-User는 재조정 스윕으로 수렴(2PC 아님).

### 소셜 최초 로그인 온보딩 (SOCIAL)

1. 인증이 `SocialIdentityProvider`로 `id_token` 검증 → subject·email(relay?). 기존 `SocialConnection` 없음 확인.
2. `RegistrationSession(SOCIAL)` + 온보딩 토큰. `provisionalContext`에 subject 보관.
3. 본인인증(위 3과 동일) + 약관 동의 버퍼링 → `complete()`(SOCIAL 스텝셋: 본인인증 ∧ 필수동의).
4. `CreateUser(..., SOCIAL)` → UserId → 인증이 `AuthAccount(ACTIVE)` + `SocialConnection.connect`. 로그인 세션 발급(아래).

### 로그인 + 세션 발급

1. `RateLimitPolicy` 검사.
2. `AuthAccount` 조회 + **접근 판정(인증-로컬)**: `userStatus(로컬 스냅샷) == ACTIVE AND lockState == NONE`. **유저 동기 호출 없음.**
3. `PasswordCredential.verify`.
   - **실패**: `LoginAttempt.record(FAILURE)` → `AccountLockPolicy` 카운터++ (임계 시 `lock(TEMP)`).
   - **성공**: `DeviceRecognitionService`(기존/신규) + `RiskEvaluator`(riskScore) → `AccountSessions.createSession`(maxN 원자, 초과 시 최오래 축출 + `ConcurrentLimitExceeded` 알림) → Access(JWT, `sid`) + Refresh 발급 → `LoginAttempt.record(LoggedIn)`. 신규 기기/지역이면 `NewDeviceDetected` 알림.

### 토큰 회전 + 재사용(탈취) 감지

- 제시 토큰이 **현재 jti**면 `rotateRefresh`(원자) → 새 Access+Refresh(`RefreshRotated`).
- **직전 토큰(유예 창 내)**이면 동일 신규 토큰 반환(정상 재시도).
- **사용/폐기 토큰(유예 밖)**이면 `revokeAll`(세션 패밀리) + `RefreshReuseDetected` 보안 알림 + 401 재로그인 요구.

### 강제/원격 로그아웃

- **사용자 원격**: `revoke(sessionId)` 또는 `revokeAllExcept(current)` → `SessionRevoked`.
- **관리자 강제**: 관리자콘솔(유저) → `ForceLogoutRequested(userId)` 통합 이벤트 → 인증 `revokeAll` + 감사 기록. 남은 토큰도 O(1) 세션검증에서 즉시 거부.

### 회원 탈퇴 + 파기

1. 유저 `User.withdraw()` → WITHDRAWN.
2. `CiRegistry.retainOnWithdrawal()`(tombstone) + `RetentionPolicy` PII 파기(법정 보존 제외).
3. `UserWithdrawn` 통합 이벤트 → 인증이 세션 `revokeAll` + 자격증명/소셜/기기 정리. 유저가 탈퇴 확인 알림.
4. 재가입 시 `CiUniquenessService`가 tombstone `withdrawnAt + cooldown`으로 재가입 정책 적용(fresh 계정).

### 상태 스냅샷 동기화 (userStatus 투영)

- 유저 = 유일 writer → 인증 `AuthAccount.userStatus`는 read-only 프로젝션이다. `UserStatusChanged`/`UserWithdrawn`(단조 version + `occurredAt`, 아웃박스 at-least-once, per-userId 순서, 멱등 소비)로만 갱신한다. cold-miss 시 동기 fallback 1회. **`WITHDRAWN` 전파 신뢰성이 핵심**이며, 지연 시에도 접근 판정이 안전측으로 차단된다.

### 정합·보장 수준

- 크로스BC 핵심 정합은 **굵은 명령 단일 트랜잭션(유저)** + 인증의 후행 생성 + 전진복구로 맞춘다. 분산 2PC 없음.
- 통합 이벤트는 아웃박스로 유실 없이 발행하되 at-least-once라 소비는 멱등해야 한다.
- 크로스 트랜잭션 부분 실패(보상 중 크래시)의 무손실 복구는 재조정 스윕으로 수렴하며, CI 유니크 제약이 이중생성 backstop이다.

---

## 도메인 이벤트

**도메인 이벤트**(서비스 내부) vs **통합 이벤트**(seam 계약, 서비스 간). 통합 이벤트는 직렬화 가능한 공개 스키마 + 단조 version + `occurredAt`을 갖는다.

| 이벤트 | 종류 | 발행 | 주요 구독 |
|---|---|---|---|
| `RegistrationStarted` / `RegistrationAbandoned` | 도메인 | 인증(온보딩) | 인증 |
| `RegistrationCompleted` | 통합 | 인증(온보딩) | 유저(회원 생성) |
| `UserRegistered` | 통합 | 유저 | 알림(환영), 감사 |
| `IdentityVerified` | 통합 | 유저 | 감사(CI 링크는 CreateUser 트랜잭션서 수행) |
| `CiLinked` / `CiRetainedOnWithdrawal` | 도메인 | 유저 | 유저 |
| `ConsentGiven` / `ConsentWithdrawn` | 통합 | 유저 | 알림설정(마케팅 동기화), 감사 |
| `ReConsentRequired` / `TermsVersionPublished` | 도메인 | 유저 | 유저(재동의 유도) |
| `NotificationPreferenceChanged` | 통합 | 유저 | 알림, 감사 |
| `RoleChanged` | 통합 | 유저 | 인증(토큰 클레임), 감사 |
| `UserStatusChanged` | 통합 | 유저 | 인증, 알림, 감사 |
| `UserWithdrawn` | 통합 | 유저 | **인증(세션·자격증명·기기 정리)**, 알림, 감사 |
| `LoggedIn` / `LoginFailed` | 도메인→통합 | 인증 | 인증(잠금), 감사, (위험 시)알림 |
| `AccountLocked` / `AccountUnlocked` | 통합 | 인증 | 알림, 감사 |
| `SessionCreated` | 도메인 | 인증 | 인증 |
| `SessionRevoked`(single/all) | 통합 | 인증 | 감사, 알림 |
| `ConcurrentLimitExceeded` | 통합 | 인증 | 알림, 감사 |
| `RefreshRotated` | 도메인 | 인증 | 인증 |
| `RefreshReuseDetected` | 통합 | 인증 | **알림(보안)**, 감사 |
| `PasswordChanged` / `PasswordReset*` | 통합 | 인증 | 알림(보안), 감사 |
| `DeviceRegistered` / `NewDeviceDetected` / `NewLocationDetected` | 통합 | 인증 | 알림, 감사 |
| `PushTokenRegistered` / `PushPermissionChanged` | 도메인 | 인증 | 알림(타겟 갱신) |
| `SocialConnected` / `SocialDisconnected` | 통합 | 인증 | 감사, 알림 |
| `ForceLogoutRequested`(명령) | 통합 | 유저(관리자) | 인증 |
| `LastLoginObserved` | 통합 | 인증 | 유저(휴면 판정) |
| `NotificationSent` / `NotificationFailed` | 도메인 | 제네릭 | 알림(재시도) |

---

## 상태 전이 요약

| 애그리거트 | 상태 흐름 |
|---|---|
| User(생명주기) | ACTIVE ↔ DORMANT, {ACTIVE, DORMANT} → WITHDRAWN. 최초 ACTIVE |
| AuthAccount(잠금 오버레이) | NONE ↔ TEMP_LOCKED, NONE ↔ ADMIN_LOCKED. 생명주기와 직교 |
| IdentityVerification | REQUESTED → VERIFIED / FAILED / EXPIRED |
| CiRegistry | ACTIVE_LINKED → WITHDRAWN_RETAINED(→ 파기) |
| ConsentRecord | 상태 없음(append), 현재값은 ConsentState 파생 |
| TermsVersion | append·불변(상태 없음) |
| Session | ACTIVE → REVOKED / EXPIRED |
| RefreshToken | ACTIVE → ROTATED / REVOKED |
| RegistrationSession | (진행) → COMPLETED / ABANDONED. 스텝셋 충족 파생 |
| Notification | PENDING → SENT / FAILED |

---

## 보존·파기 스케줄

아래는 데이터별 보존기간·파기 규칙·**법령 근거**다(조사일 2026-07-14, 현행 조문 확인). 정책값은 `RetentionPolicy` 공용 설정으로 버전관리·외부화한다. **데이터 소유 = 파기 소유**(각 서비스가 자기 데이터 `PurgeJob` 집행, 탈퇴 파기는 `UserWithdrawn`/`RetentionPurgeDue` 팬아웃 조정).

| 항목 | 소유 | 보존기간 | 법령 근거 | 파기 규칙 |
|---|---|---|---|---|
| 세션(Redis) | 인증 | 만료 즉시(Refresh TTL 내) | 개보법 §21(불필요 시 지체없이) | 로그아웃/만료 즉시 무효화 |
| 로그인이력·IP(LoginAttempt) | 인증 | **2년**(안전측) | 안전성 확보조치 기준 §8① | 창 경과 후 파기, IP 90일 후 가명화 |
| 감사로그(AuditLog) | 제네릭 | **2년** | 안전성 확보조치 기준 §8 | WORM 유지, 창 후 PII crypto-shred |
| 동의이력(ConsentRecord) | 유저 | 회원유지 + 탈퇴 후 **5년** | 개보법 §22(동의 입증책임)·상사시효 5년 | append-only 유지 후 기한 파기 |
| 본인인증 평문 PII | 유저 | 목적 달성 즉시 최소화(회원 유지 최소분만) | 개보법 §21·§24, 최소수집 | 불필요분 5일 내 파기 |
| CI 해시(CiRegistry) | 유저 | 회원 존속 + 탈퇴 후 유한(기본 6개월) | 정통망법 §23의2/3 + 연계정보 기준 고시 | 암호화·분리보관, 창 경과 후 파기 |

- 파기 시한: 개보법 §21 "지체 없이" + 표준지침 "5일 이내". 유출 통지: 개보법 §34 72시간. 휴면 강제(유효기간제)는 2023.9.15 폐지 → 서비스 자율. CI/DI는 '고유식별정보' 미포함이나 주민번호 파생이라 강한 안전조치 대상(법정 최소보존 없음). 전자상거래법은 순수 인증서비스에 **비적용**(거래는 범위 밖).

---

## 영속·마이그레이션 유의

도메인 모델 확정 중 빌드·기동이 강제하는 항목이다. 구현 계획이 반영한다.

- **저장소 배치**: RDB(JPA) = User·IdentityVerification·CiRegistry·Terms·Consent·NotificationPreference·RBAC·AuthAccount·PasswordCredential·SocialConnection·Device·LoginAttempt·Notification·AuditLog. **Redis** = AccountSessions/Session·VerificationChallenge·RegistrationSession(+연속실패 잠금 카운터).
- **유니크 제약(RDB)**: `auth_account.login_email`(정규화, 활성 유니크), `ci_registry.ci_hash`(전역), `social_connection(provider, provider_user_id)` + `(user_id, provider)`, `device(user_id, fingerprint)`, `terms_version(type, version)`, `terms_document.type`, `role.name`, `permission(resource, action)`, `user_role(user_id, role_id)`, `notification_preference_entry(preference_id, channel, category)`, `consent_state(user_id, terms_type)`.
- **암호화 컬럼(AES-GCM/KMS)**: `users`의 name·birth_date·phone, `identity_verification`의 name·birth_date·phone·di. **CI는 해시 컬럼만**(원문 컬럼 없음).
- **append-only 테이블**: `consent_record`·`login_attempt`·`audit_log`(update/delete 금지, PII만 crypto-shred/가명화).
- **낙관락(`@Version`)**: RDB엔 기본 미도입(관리자 저경합). 동시 경합 지점(세션 상한·리프레시 회전)은 **Redis 원자 연산**으로 처리(RDB 락 아님).
- **논리 FK**: 모든 `xxx_id`(user_id·device_id·role_id·terms_version 참조 등)는 물리 FK 없이 인덱스만 Flyway로 생성한다(크로스 서비스 JOIN 금지).
- **스키마 분리**: `usr`·`auth`·`generic` 3개 스키마를 분리(미래 서비스 경계). 크로스 스키마 조인 금지 — ID 참조 + 계약/이벤트만. 통합 이벤트는 처음부터 직렬화 공개 스키마 + 아웃박스.

---

## 핵심 설계 결정 (왜 이렇게)

비-자명한 선택의 **근거와 기각 대안**을 남긴다 — 모델 구조만으론 복원되지 않는 "왜"다. 나중에 재론(re-litigation)을 막는 게 목적이다.

### 경계·정체성

| 결정 | 근거 | 기각한 대안 |
|---|---|---|
| **UserId = 유저 소유 캐노니컬**, 인증은 참조 | 생명주기·개인정보 응집을 유저에, 로그인 자율성을 인증에 | 인증 소유(AccountId)·유저는 프로필만 |
| **loginEmail(인증·식별자) ≠ contactEmail(유저·연락)** 분리 | 로그인 핫패스가 유저 호출 없이 동작 + 알림 수신자 소스 단일화(릴레이 이메일 대응) | 유저 단일 이메일(로그인마다 조회·식별자와 채널 융합) |
| **본인인증/CI = 유저 소유** | 사람 식별·개인정보·중복방지 응집 | 인증 소유(안티프로드 관점) |
| **RBAC 배정=유저 / 집행=인증**(토큰 클레임) | 회원관리(관리자 콘솔)와 응집, 핫패스는 클레임으로 | IdP형 인증 소유 RBAC |
| **Device=인증 / NotificationPreference=유저** | 기기는 세션 바인딩(인증), 수신 동의는 개인정보(유저) | 별도 알림 서비스로 타깃·설정 통합 |

### 상태·정합

| 결정 | 근거 | 기각한 대안 |
|---|---|---|
| **잠금=직교 오버레이(인증)** / 생명주기(유저) 분리 | "휴면 중 잠금" 등 조합을 단순 판정식으로, 두 축을 한 컬럼에 안 겹침 | LOCKED를 생명주기 상태로 + previousStatus 보존 |
| **인증-로컬 `userStatus` 스냅샷**(이벤트 동기화), 매 로그인 동기호출 금지 | 로그인 핫패스 자율성·Core 가용성 디커플. 탈퇴 전파 지연 시에도 안전측 차단 | 매 로그인 유저 동기조회(중요도 역전) |
| **온보딩=단일 `CreateUser(bundle)` 굵은 명령**(유저가 한 트랜잭션서 UserId 채번·동의·CI링크) | userId-선참조 불변식 위반 제거, 두 미래 서비스 트랜잭션 하드커플 방지 | fine-grained 원격 write(고아 레코드·분산 write) |
| **영속 PENDING 계정 금지**, 온보딩 전용 토큰만 | 좀비 계정·고아 소셜연동 방지 | durable PENDING + TTL 청소 |
| **AccountSessions 애그리거트**(동시세션 정합 경계), Redis 원자 | 다중 로그인 임계를 원자 보장 | Session별 애그리거트 + 분산 락 |
| **리프레시 재사용 유예 창** | 정상 재시도(모바일/네트워크) 오탐 방지 | 유예 없는 즉시 무효화(오탐 위험) |
| **LoginAttempt(이력) ≠ AuditLog(감사)** 분리 | 질의 패턴·보존·접근통제 상이 | 단일 로그 테이블 겸용 |

### 정책·컴플라이언스 (확정값)

| 결정 | 값 | 근거 |
|---|---|---|
| 휴면 전환 | 1년 + 30일 전 통지 + OTP 해제 | 유효기간제 폐지(2023.9.15)로 서비스 자율. 표준 관행 최보편값 |
| 재가입 | fresh 계정(이력 미승계) + 쿨다운 30일 | 어뷰징·명의도용 방지, 대형 사례 관행(30~60일) |
| CI tombstone | 유한 6개월 후 완전 파기(무기한 미채택) | 부정재가입 방지 최소보존, 대형 사례 전부 유한 |
| 접속기록(로그인이력) | 2년 | 안전성 확보조치 기준 §8①(5만명↑·CI/DI 준식별정보 가정, 안전측) |
| 동의이력 | 탈퇴 후 5년 | 개보법 §22 입증책임·상사시효 5년 정합 |
| 전자상거래법 | 비적용 | 순수 인증서비스, 거래는 범위 밖·상위 플랫폼 소관 |
| 보안 알림 강제 | SECURITY는 수신거부 무시 | 계정탈취·유출 통지 의무(개보법 §34) |

> 위 확정값은 리서치 기반 채택값이다. 프로덕션 배포 전 사내 법무의 형식 최종검토를 권고하되, 개발은 이 값으로 진행한다. 세부 법령 근거는 [보존·파기 스케줄](#보존파기-스케줄).

---

## 확정된 결정 (요약)

애그리거트별 핵심 결정을 한 줄로 요약한다(결정 *근거·기각 대안*은 위 [핵심 설계 결정](#핵심-설계-결정-왜-이렇게)).

- **정체성 seam**: `UserId` = 유저 소유 캐노니컬, 인증은 참조. `AuthAccount`는 `userId` PK로 회원과 1:1. `loginEmail`(식별자·인증) ≠ `contactEmail`(연락·유저) 별개.
- **회원(User)**: `LifecycleStatus{ACTIVE, DORMANT, WITHDRAWN}`, PII는 본인인증분·암호화, 휴면 1년(사전통지 30일·OTP 해제·분리보관), 탈퇴는 status=WITHDRAWN + PII 즉시 파기, 재가입 fresh·쿨다운 30일. 역할은 `user_role` 조인.
- **본인인증/CI**: `IdentityVerification`(암호화·VerificationId 키) + `CiRegistry`(ciHash 전역 유니크). CI 원문 미저장, tombstone 유한 6개월, `CreateUser` 트랜잭션서 유니크 hard-enforce(soft-check는 조기 실패용).
- **약관·동의**: `TermsVersion` append·불변 + `(type, version)` 유니크, `ConsentRecord` append-only 영구 + `ConsentState` fold 읽기모델, 필수 철회는 탈퇴 경로.
- **알림설정/RBAC**: `NotificationPreference` 채널×카테고리 opt-in(보안 강제), 배정=유저(`Role`/`Permission`/`user_role`)·집행=인증(토큰 클레임).
- **인증계정(AuthAccount)**: `loginEmail` 유니크, `LockState{NONE, TEMP_LOCKED, ADMIN_LOCKED}` 직교 오버레이, `userStatus` 읽기전용 투영(단조 version), ≥1 로그인 수단 유지, 접근 판정 인증-로컬(유저 동기호출 없음), 실효상태 합성 읽기모델.
- **자격증명**: `PasswordCredential` Argon2id/BCrypt + 최근 N 재사용 금지 + resetToken(해시·TTL); `SocialConnection` `(provider, subject)` 유니크 + 애플 특수 + ≥1 수단 유지.
- **세션(AccountSessions)**: Redis 진실원본, 동시 ≤ N 원자·최오래 축출, 세션 O(1) 검증, 리프레시 회전 + 유예 창 + 재사용 시 패밀리 무효화. 세션=리프레시 패밀리 경계.
- **디바이스**: `(user_id, fingerprint)` 유니크, 삭제→세션 종료, 푸시 토큰·권한(pushEnabled) 보유(발송 판정에 인증 측 AND).
- **온보딩(RegistrationSession)**: Redis TTL, 영속 PENDING 금지, PII 원문 미보유(verificationRef), 완료=가입유형별 스텝셋(LOCAL/SOCIAL), 굵은 명령 `CreateUser(bundle)`.
- **제네릭**: `Notification` 발송=수신설정(유저) AND 기기권한(인증) AND 카테고리(보안 강제), 수신자=유저 contact; `AuditLog` append-only·전/후 값·WORM.
- **정합**: 크로스BC는 굵은 명령 단일 트랜잭션 + 전진복구 + 멱등키(RegistrationId) + CI 유니크 backstop. 통합 이벤트 아웃박스·멱등 소비.

---

## MFA 확장 슬롯

자리만 확보하고 본 범위에서는 구현하지 않는다(요구사항: 범위 밖·향후 확장).

- **인증수단 패밀리 확장**: 추상 `AuthenticationFactor` 아래 `TotpCredential`을 형제로 추가(컬럼 추가가 아니라 팩터 형제 추가, 다형 팩터 테이블 `type` 컬럼 예약).
- **챌린지 타입 확장**: `ChallengeType`에 `MFA` 추가(코드/백업코드).
- **위험 기반 스텝업**: `RiskEvaluator`가 위험 임계 초과 시 스텝업 챌린지 트리거(예약 훅).
- **예약 이벤트**: `MfaEnrolled`, `MfaChallengeIssued`, `MfaChallengeVerified`.

---

## 도메인 용어집 (예약어 divergence)

코드마다 갈리지 않도록 표준 divergence를 등재한다. 아래 외 divergence는 임의로 만들지 않는다.

| 도메인 개념 | 엔티티 | 스키마 | 테이블 | 사유 |
|---|---|---|---|---|
| 회원 | `User` | `usr` | `users` | `USER`는 다수 DB 예약어. 스키마·테이블 모두 회피 |
| 세션 | `Session` | — | (Redis) | 테이블 아님(Redis 키). 예약어 회피 불필요 |

나머지 애그리거트는 예약어가 아니라 스키마·테이블이 도메인/엔티티명과 일치한다.

---

## 명시적 범위 밖

- **MFA/2단계 인증**(TOTP·SMS OTP·백업코드) — 슬롯만 확보, 구현 제외.
- 결제·정산, 서비스 도메인 고유 비즈니스 로직, 프론트엔드 화면.
- 코드/패키지 구조·ORM 매핑 상세·Redis 키 스키마·Lua 스크립트·배포 토폴로지·API 스펙(구현 규약·계획 소유).
- 반환 형상·ErrorCode·HTTP status·트랜잭션 배선·아웃박스/메시징 인프라 상세(도메인은 "이벤트→구독"만 규정).
- 소셜 provider 추가·위험기반 인증 고도화(Future).
- 관리자 콘솔 UI·마스킹 렌더 상세(마스킹 정책은 규정, DTO 렌더는 구현).
