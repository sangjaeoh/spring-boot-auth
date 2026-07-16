# Threat Model

## 언제

- 새 엔드포인트·자산·신뢰 경계를 추가해 위협 표면이 바뀔 때.
- 보안 테스트(SCA·펜테스트) 범위를 정하거나 수용 위험을 재평가할 때.

## 자산·신뢰 경계

- 보호 자산: 자격증명(Argon2 해시·비밀번호 이력), PII(봉투암호문·blind index), CI/DI(중복가입 방지 키), 세션·리프레시 토큰(Redis), JWT 서명키(keyring 봉투암호문), KEK·pepper(시크릿 매니저), 감사 로그(WORM).
- 신뢰 경계: 공개 인터넷 ↔ app-api(8080) / 내부망 ↔ app-admin(8081)·관리 포트(9404·9405) / 앱 ↔ PostgreSQL·Redis / 앱 ↔ 외부 기관(소셜 OIDC·본인확인·알림 벤더).

## STRIDE 분석 (경계별 위협 → 구현된 완화)

| 위협 | 표면 | 완화(구현 지점) |
| --- | --- | --- |
| Spoofing | 로그인·토큰 위조 | Argon2id KDF + 동시성 상한(`ConcurrencyLimiter`), JWT 서명·JWKS 회전(90일·grace), 매 요청 세션 저장소 검증(O(1)) |
| Spoofing | 리프레시 탈취 재사용 | 회전 + 재사용 감지 시 패밀리 전멸(`rotate_session.lua`) + 보안 알림 |
| Tampering | 감사 이력 조작 | AuditLog WORM 트리거(UPDATE/DELETE 차단), append-only ConsentRecord |
| Tampering | 마이그레이션·이미지 변조 | 적용된 마이그레이션 수정 금지 + expand-contract(deployment.md), 컨테이너 안 래퍼 빌드 |
| Repudiation | 인증·관리자 행위 부인 | 로그인 이력(LoginAttempt)·인증 이벤트 감사 append·관리자 전/후 값 감사 |
| Information disclosure | PII 유출(DB·백업) | AES-GCM 봉투암호(KEK 시크릿 매니저 주입 — deployment.md), 전화 blind index(pepper 분리 보관), 마스킹 조회 |
| Information disclosure | 계정 열거 | 로그인 단일 401(비존재 계정도 dummy KDF로 타이밍 패리티 — `PasswordCredentialReader.verifyAbsent`), 재설정 무차별 200 + 무작위 challengeId. 검증: `AuthApiE2EIT.nonexistentAccountLoginIsIndistinguishableFromWrongPassword`, `PasswordApiE2EIT.initiateReturnsUniformlyForUnknownEmail` |
| Denial of service | 로그인 폭주·KDF 소진 | Argon2 동시 실행 상한 + 대기 초과 503, 민감 엔드포인트 레이트리밋, 연속 실패 잠금(TEMP_LOCKED), 인증코드 일일 한도·쿨다운 |
| Denial of service | Redis 장애 오픈 | fail-closed(세션 검증 불가 시 401/503) + 연결단 타임아웃 250ms + revoke WAIT 내구 확인 |
| Elevation of privilege | 관리자 권한 상승 | RBAC(roles 클레임 + `@PreAuthorize` SUPER_ADMIN 가드), RoleChanged 재발급 반영 + 강제 로그아웃 병행, 관리 포트 내부망 격리 |

## timing-safe 비교 점검 결과

- 상수시간 비교 적용: 온보딩 토큰 해시(`RegistrationSessionProcessor` — `MessageDigest.isEqual` + 부재 세션 패리티), 비밀번호(Argon2 `matches` + 비존재 계정 dummy KDF).
- 수용(비상수시간이나 위험 미미로 판단): Redis Lua의 해시 등호 비교(`verify_challenge.lua` 코드 해시, `rotate_session.lua` 리프레시 JTI 해시).
  - 비교 대상이 SHA-256 해시라 원문 접두 일치가 비교 시간에 반영되지 않고, 시도 상한·TTL·단일 사용이 이차 방어로 있다. Redis 단일 스레드 실행이라 네트워크 지터 대비 신호가 무의미하다.
- 수용: 비밀번호 재설정의 발송 유무 타이밍 차이(무발송 경로가 빠름) — 레이트리밋으로 완화, 균등 지연 도입은 기각(지연 자체가 신호가 된다).
- 정책 결정(열거 저항 예외): 가입·이메일 변경의 중복 이메일 409는 의도된 공개다 — 온보딩 UX(재로그인 유도)가 열거 저항보다 우선하고, 인증코드 일일 한도·레이트리밋이 대량 열거를 막는다.

## SCA (소프트웨어 구성요소 분석)

- Dependabot이 Gradle 의존성·GitHub Actions를 주간 스캔해 PR을 올린다(`.github/dependabot.yml`).
- 보안 업데이트 PR은 `./gradlew build` 게이트 통과를 전제로 우선 머지한다. major 승격은 별도 검토한다.
- 버전 정본은 `gradle/libs.versions.toml` 하나라 갱신 지점이 단일하다(BOM 관리 버전은 spring-boot 갱신으로 따라온다).

## 외부 보안 게이트

- 펜테스트(외부 업체)·PIA 판정은 코드 밖 조달·조직 항목이다 — 오너·기한은 [prod-gate](prod-gate.md)가 소유한다.
