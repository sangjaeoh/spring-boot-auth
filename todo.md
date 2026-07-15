# TODO — 요구사항 구현 완료까지 남은 작업

`REQUIREMENTS.md` 전체 요구사항을 구현 완료하기 위해 남은 작업을 **추천 작업 순서대로** 나열한다. 각 항목의 착수 프롬프트는 [`todo-prompt.md`](todo-prompt.md)에 있다 — 새 세션에서 해당 프롬프트를 복사해 시작한다.

작업 완료 시 이 목록의 해당 항목을 체크하고, `IMPLEMENTATION_PLAN.md`의 "진행 현황"도 함께 갱신한다.

## 작업 목록 (추천 순서)

- [ ] **0. (병렬·비코딩) 외부 조달 워크스트림 트래킹** — 본인인증기관 계약·소셜 4사 앱 심사·Apple Developer·법무(PIPA/PIA) 조달 현황 문서화. 수 주~수개월 리드타임으로 P2/P4의 크리티컬 패스 — 코딩과 병렬로 지금 개시
- [ ] **1. Phase 1b — 온보딩 플로우 슬라이스** — RegistrationSession(Redis TTL) + 스텝 마킹 + Mock 본인인증(`external-identity`) + 필수동의 버퍼(Terms/Consent 시드) + 온보딩 전용 토큰
- [ ] **2. Phase 1b — CreateUser 단일 크로스스키마 트랜잭션 슬라이스** — usr+auth 한 ACID 트랜잭션으로 정회원 생성(고아-User 불가·CI 중복 거부). **이 항목 완료 = 요구사항 Phase 1(코어 로컬 인증 MVP) 완성**
- [ ] **3. Phase 2 — 소셜 로그인 4종 + 연동/해제** — 카카오/네이버/구글/애플 OIDC(`external-social`) + 소셜 온보딩 + ≥1 로그인 수단 유지 + 애플 특수사항
- [ ] **4. 이벤트 인프라 — common-messaging·infra-messaging** — MessagePublisher 포트 + 멱등 소비 + 내구 재시도(DLQ). P3의 `ForceLogoutRequested` 소비 등 크로스 도메인 리스너의 전제
- [ ] **5. Phase 3 — 디바이스 + 다중 로그인 제한 + 강제/원격 로그아웃** — Device 등록/인식·동시 세션 ≤N 원자 축출·세션 목록/원격 로그아웃·관리자 강제 종료 훅 + Redis failover revoke 유실 차단(WAIT/WAITAOF, 1a 이연분)
- [ ] **6. Phase 4 — 본인인증(실) + 약관·동의 + 탈퇴/보존 + 휴면** — 실 기관 어댑터·약관 버전/재동의·동의 철회·탈퇴(PII 파기·CI tombstone·fail-closed)·재가입 쿨다운·휴면(OTP 해제)·`app-batch`
- [ ] **7. Phase 5 — 이상탐지·알림 + 레이트리밋 + 계정 잠금** — `domain-generic`(Notification)·`external-notification`(실)·GeoIP·riskScore·발송 판정 정책·수신설정 API·레이트리밋·연속 실패 잠금
- [ ] **8. Phase 6 — 관리자 콘솔 + RBAC + 감사** — `app-admin`·RBAC 배정/집행·회원 검색(마스킹)·강제 로그아웃·AuditLog(WORM)·실효 회원상태 읽기모델
- [ ] **9. Phase 1a 이연 하드닝** — JWKS 자동 90일 회전 + 내구 키스토어 + 다중 인스턴스 공유, revokeAll 단일 Lua 원자화, Lettuce 연결단 타임아웃, change/resetTo KDF 트랜잭션 밖 이동
- [ ] **10. 운영·보안 게이트 (prod 배포 전 비협상)** — 부하테스트·관측성/SLO·CI/CD(expand-contract)·시크릿 관리·DR/HA·보안 테스트(STRIDE·펜테스트·SCA)·컴플라이언스(법무·PIA)·OpenAPI 계약

## 범위 밖

- **Phase 7 — MFA**: 구현하지 않는다. `AuthenticationFactor` 형제·`ChallengeType.MFA`·스텝업 훅·예약 이벤트 슬롯만 유지(이미 확보됨).
