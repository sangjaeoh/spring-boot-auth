# Observability·SLO

## 언제

- 대시보드·알람을 구성하거나 SLO 위반을 판정할 때.
- 지표를 추가·변경할 때(이름·태그 계약 확인).

## 노출 계약

- app-api(9404)·app-admin(9405)은 분리된 관리 포트에 `health`(liveness/readiness 프로브 포함)·`prometheus`만 노출한다. 관리 포트는 내부망 전용이다(체인 permitAll — 격리는 네트워크 소유).
- app-batch는 run-to-completion 잡이라 HTTP 관측 대상이 아니다. 잡 성패는 종료코드·구조적 로그로 관측한다(스케줄러가 소유).
- 보안 탐지 카운터는 app-api `SecurityMetrics`가 도메인 이벤트에서 계측한다. 태그는 유계 enum만 쓴다(카디널리티 통제).

  | 지표(Prometheus 이름) | 의미 | 알람 용도 |
  | --- | --- | --- |
  | `auth_login_success_total` | 로그인 성공 수 | 실패율 분모 |
  | `auth_login_failure_total{reason}` | 로그인 실패 수(사유별) | 크리덴셜 스터핑·실패율 |
  | `auth_refresh_reuse_detected_total` | 리프레시 재사용(탈취) 감지 수 | 급증 알람 |
  | `auth_account_locked_total` | 연속 실패 일시 잠금 수 | 잠금율 알람 |
  | `http_server_requests_seconds_bucket{uri}` | URI별 지연 히스토그램 | p99·가용성 |

## SLO (정본 — REQUIREMENTS.md 비기능 요구사항의 수치화)

- 아래 목표는 부하 실측 리포트([load-report](load-report.md))로 보정한다. 로컬 실측(2026-07-16)은 전 항목 예산 내다.

  | SLI | 목표 | 산식 |
  | --- | --- | --- |
  | 인증 API 가용성 | 월 99.9% | 5xx 제외 응답 / 전체 (`http_server_requests`) |
  | 로그인 p99 | ≤ 1s (Argon2 KDF 포함) | `http_server_requests_seconds_bucket{uri="/auth/login"}` |
  | 보호 API 세션 검증 오버헤드 p99 | ≤ 100ms | 보호 URI 버킷(세션 조회 O(1) + Redis 타임아웃 250ms 유계) |
  | 토큰 재발급 p99 | ≤ 300ms | `uri="/auth/token/refresh"` 버킷 |

## 알람 기준

- 재사용 감지 급증: `increase(auth_refresh_reuse_detected_total[5m]) > 10` — 탈취 캠페인 신호, 즉시 페이지.
- 잠금율 급증: `increase(auth_account_locked_total[15m])`가 평시 기준선의 5배 초과 — 크리덴셜 스터핑 신호.
- 로그인 실패율: `failure/(success+failure) > 0.4`가 15분 지속 — 공격 또는 장애 신호.
- fail-closed 503 비율: `http_server_requests{status="503"}` 비율 > 1%가 5분 지속 — Redis 장애 신호(fail-closed 설계라 503은 가용성 소진으로 계상).
- 대시보드·알람 룰의 구체 배선(Prometheus·Grafana 등 수집기 구성)은 배포 환경 소유다 — 이 문서는 지표 계약과 임계만 소유한다.
