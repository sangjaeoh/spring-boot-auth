# TODO — 요구사항 전체 구현 완료까지

2026-07-15 코드 실사 결과: `REQUIREMENTS.md` Phase 1의 기반은 대부분 완결됐다 — 빌드 하네스(컨벤션 플러그인 계층 강제 + Spotless·NullAway·Error Prone·ArchUnit), 인증 핫패스(로그인 → O(1) 세션 검증 → 로그아웃 즉시 401, 리프레시 회전 + 유예 창 + 재사용 감지 시 패밀리 전멸), 비밀번호 생명주기(재설정·변경·최근 N 재사용 금지), JWKS 게시 + 회전 키링, Argon2 동시성 바운드, Redis fail-closed·단일 슬롯 규약, usr 스키마 + PII 봉투암호(AES-GCM)·전화 blind index, User·IdentityVerification(Mock)·CiRegistry·약관 시드 + 온보딩 플로우(RegistrationSession Redis TTL·이메일/휴대폰 코드·동의 버퍼·온보딩 전용 토큰 — 엔드포인트 15개, 단위·Testcontainers 통합·E2E green). 남은 공백: 가입을 정회원으로 커밋하는 라우트가 없어 Phase 1이 아직 닫히지 않았고(CreateUser 단일 크로스스키마 트랜잭션 — ConsentRecord 영속·CiRegistry.link writer 포함), Phase 2~6 전부(소셜 4종·디바이스·동시 세션 상한·강제/원격 로그아웃·본인인증 실·약관 정식화·탈퇴/휴면·알림·레이트리밋·계정 잠금·관리자·RBAC·감사 — 17개 애그리거트 중 ConsentRecord/ConsentState·NotificationPreference·RBAC·SocialConnection·Device·Notification·AuditLog 미구현)와 크로스 도메인 이벤트 인프라, 1a/1b 이연 하드닝, 운영·보안 게이트가 남아 있다. 항목별 상세 작업 요청 프롬프트는 [`todo-prompt.md`](./todo-prompt.md)에 있다. 완료된 슬라이스별 확정 결정·이연 경위의 원문 기록은 git 이력의 IMPLEMENTATION_PLAN.md(93fa806 직전 버전)에 있다.

사용법: 새 세션에서 항목 하나를 골라 `todo-prompt.md`의 해당 프롬프트를 그대로 붙여넣는다. 각 프롬프트는 완료 시 이 파일의 해당 항목 체크([x])와 커밋·메인 머지·잔여 브랜치 삭제까지 포함한다.

권장 실행 순서: **1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10**. 1(CI)을 맨 앞에 두는 이유: 가장 작은 독립 작업이면서 이후 모든 항목이 원격 게이트 위에서 돌게 된다. 2는 Phase 1을 닫는 크리티컬 패스이자 3의 전제다(소셜 온보딩이 CreateUser 트랜잭션을 재사용). 4(이벤트 인프라)는 5~8의 크로스 도메인 발행·소비(ForceLogoutRequested·UserWithdrawn·RoleChanged·보안 알림 구독)의 전제다. 5~8은 요구사항 Phase 3~6 순서 그대로다 — 6이 만든 NotificationPreference·연락처 진실원본을 7의 발송 판정이 쓰고, 7의 알림·잠금 전이를 8의 관리자 오퍼레이션이 쓴다. 9는 기능과 독립이라 틈새 어느 때든 가능하되 prod 전 필수. 10은 전 기능 위의 최종 게이트라 마지막.

공통 완료 기준(모든 항목): `docs/` 4문서 규칙 준수 + `docs/architecture.md` "빌드가 강제하는 불변식" 자기검증, 기존 컨트롤러·파사드·테스트 패턴 준수, `./gradlew build` 게이트(Spotless·NullAway·Error Prone·ArchUnit·전체 테스트) 통과, 요청 범위 밖 기능 추가 금지. 필드·상태·정책·불변식·오퍼레이션의 정본은 `DOMAIN_MODEL.md`다 — 프롬프트 요약과 다르면 정본을 따른다. 큰 항목은 수직 슬라이스로 쪼개 슬라이스마다 검증·머지한다.

코드 밖 전제(코딩과 병렬 진행): 본인확인기관 계약(NICE/KG/다날 중 선정·사이트코드), 소셜 4사 개발자앱 등록·심사, Apple Developer(.p8), 법무(처리방침·약관 원문·PIPA 검토·PIA 판정)는 수 주~수개월 리드타임으로 3·6번의 실 연동과 10번 prod 게이트의 크리티컬 패스다. 개발은 Mock으로 비블로킹이므로 조달은 코딩과 병렬로 별도 트래킹한다.

의도적 제외: Phase 7 MFA — 구현하지 않는다. AuthenticationFactor 형제·ChallengeType.MFA·위험 기반 스텝업 훅·예약 이벤트 슬롯만 유지한다(`DOMAIN_MODEL.md` "MFA 확장 슬롯").

## 게이트·코어 완성 (요구사항 Phase 1 마감)

- [ ] **1. CI 파이프라인** — `.github` 부재로 품질 게이트(Spotless·NullAway·Error Prone·ArchUnit·Testcontainers 테스트)가 로컬 `./gradlew build`에만 존재. GitHub Actions push/PR 게이트 강제.
- [x] **2. 가입 완료 — CreateUser 단일 크로스스키마 트랜잭션** — 온보딩 `complete()`와 시드 전용 프로비저닝 파사드만 있고 정회원 커밋 라우트가 없다. usr(User·ConsentRecord append·CiRegistry.link 유니크 hard-enforce·IdentityVerification 연결) + auth(AuthAccount·PasswordCredential)를 한 ACID 트랜잭션으로 원자 생성. **이 항목 완료 = 요구사항 Phase 1(코어 로컬 인증 MVP) 완성**

## 인증 수단·세션 통제 (Phase 2·3)

- [x] **3. 소셜 로그인 4종 + 연동/해제** — 카카오/네이버/구글/애플 OIDC(`external-social` 신설, dev Mock) + 소셜 최초 로그인 온보딩(SOCIAL 스텝셋·CreateUser 재사용) + ≥1 로그인 수단 유지(동시 해제 직렬화) + 애플 특수사항(.p8 동적 client_secret·릴레이 이메일).
- [x] **4. 이벤트 인프라(common-messaging·infra-messaging)** — 현재 이벤트는 in-process ApplicationEventPublisher + 구조적 로깅뿐. MessagePublisher 포트 + 통합 이벤트 공개 스키마(단조 version·occurredAt) + 멱등 소비 + 내구 재시도(DLQ). 5~8의 크로스 도메인 발행·소비 전제.
- [x] **5. 디바이스 + 다중 로그인 제한 + 강제/원격 로그아웃** — Device 애그리거트(현재 세션의 deviceId는 placeholder)·기기 인식·동시 세션 ≤3 원자 검증 + 최오래 축출·내 세션 목록/원격 로그아웃 API·기기 삭제→세션 종료·ForceLogoutRequested 소비·revoke failover 유실 차단(WAIT, 1a 이연분).

## 회원 생명주기·컴플라이언스 (Phase 4)

- [ ] **6. 본인인증(실) + 약관·동의 정식화 + 내 정보 + 탈퇴·보존 + 휴면** — 실 기관 어댑터 스위치·약관 버전 발행/재동의 게이트·동의 철회 + ConsentState 읽기모델·NotificationPreference 생성·내 정보 조회/수정·탈퇴(PII 즉시 파기·CI tombstone·fail-closed)·재가입 쿨다운(30일)·휴면(12개월·OTP 해제)·`app-batch` 신설(휴면 전환·보존 파기).

## 보안 운영·관리 (Phase 5·6)

- [ ] **7. 이상탐지·알림 + 레이트리밋 + 계정 잠금** — `domain-generic`(Notification) 신설·발송 판정 정책(수신설정 AND 기기권한 AND 카테고리, 보안은 opt-out 무시)·수신설정 API·GeoIP/RiskEvaluator(riskScore 산출)·민감 엔드포인트 레이트리밋·인증코드 일일 한도/재발송 쿨다운(1b 이연분)·연속 실패 잠금(TEMP_LOCKED) + 쿨다운 자동 해제.
- [ ] **8. 관리자 콘솔 + RBAC + 감사** — `app-admin` 신설·Role/Permission/user_role(현재 roles 하드코딩 제거·가입 시 USER 배정)·RoleChanged→클레임 갱신·회원 검색(blind index)/상세(마스킹)·강제 로그아웃 발행·잠금/해제(ADMIN_LOCKED)·AuditLog(WORM·전/후 값)·실효 회원상태 읽기모델.

## 하드닝·운영 게이트

- [ ] **9. 이연 하드닝(1a·1b)** — JWKS 자동 90일 회전 + 내구 키스토어 + 다중 인스턴스 공유(현재 인메모리·단일 인스턴스 한정·재시작 시 직전 Access 401), revokeAll 단일 Lua 원자화, Lettuce 연결단 타임아웃, change/resetTo KDF를 tx 밖으로, 챌린지 발급 Redis 장애 503 정합, 로그인 DTO @Email 간극.
- [ ] **10. 운영·보안 게이트(prod 배포 전 비협상)** — 부하테스트(핫패스 p99·Lua 경합·Argon2 메모리 예산)·관측성/SLO·컨테이너화/CD(app-migration init·expand-contract)·시크릿 관리(pepper·KEK·서명키·.p8 — 현재 dev 정적 키 평문)·DR/HA(백업/PITR·RTO/RPO·런북)·보안 테스트(STRIDE·펜테스트·SCA·열거 저항)·컴플라이언스(법무·PIA)·OpenAPI 계약.
