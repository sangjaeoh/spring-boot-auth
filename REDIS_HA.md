# Redis HA·failover 정합 결정 (Phase 1a 횡단 게이트)

세션은 요청 100% 핫패스이자 진실원본이다(REQUIREMENTS 비기능·DOMAIN_MODEL 기준선). 이 문서는 IMPLEMENTATION_PLAN §5 "Redis HA(P1 확정)"·§6 "Redis 진실원본의 failover 정합"이 P1a 전 확정하라고 지정한 결정을 소유한다. 코드로 검증 가능한 부분(fail-closed 판정·Lua keyslotting)은 테스트가 강제하고, 실 인프라 의존 부분(토폴로지·지속성·복제)은 여기서 결정·운영설정으로 명문화한다.

- 규칙 문서(`docs/`의 네 문서)는 개발 규칙을 소유한다. 이 문서는 규칙이 아니라 운영·아키텍처 결정 레코드이므로 루트에 둔다(REQUIREMENTS·DOMAIN_MODEL·IMPLEMENTATION_PLAN과 같은 층위).

## 결정 요약

| 항목 | 결정 | 강제/근거 |
|---|---|---|
| 토폴로지 | Redis **Cluster** | userId 자연 샤딩·해시태그 이미 배선. dev/test는 단일 노드 |
| Lua keyslotting | 세션 애그리거트 전 키 `{u:userId}` 단일 슬롯 | `SessionKeysTest`가 강제(CROSSSLOT EVAL 불가) |
| 지속성 | AOF `appendonly yes`, `appendfsync everysec` | 손실 창 ≤ 1초 + 복제 지연 |
| revoke 유실 방지 | `min-replicas-to-write`·`min-replicas-max-lag`로 창 축소, 완전 차단은 WAIT(후속) | 비동기 복제서 revoke는 재생성 불가라 보안 크리티컬 |
| fail-closed(읽기) | `validate` Redis 불가 → `false` → 401 | `RedisSessionStore.validate`·`RedisSessionStoreFailClosedTest` |
| fail-closed(쓰기) | `create`·`rotate`·`revoke`·`revokeAll` Redis 불가 → 503, 토큰 미발급 | `AuthErrorCode.SESSION_STORE_UNAVAILABLE`·동일 테스트 |
| 명령 타임아웃 | `spring.data.redis.timeout: 250ms` | 정상 연결 명령 상한(연결단 stall은 후속) |

## 토폴로지: Cluster

- 세션 애그리거트는 userId로 자연 샤딩된다 — 한 사용자의 모든 세션 키가 `{u:userId}` 해시태그로 한 슬롯에 모여, 그 사용자 대상 회전·무효화가 단일 노드에서 원자적으로 끝난다. 수평 확장·샤드별 장애 격리에 유리하다.
- 코드는 이미 이 방향으로 배선됐다(해시태그). Sentinel(단일 마스터)에서도 해시태그는 무해하므로, Cluster를 기본 결정으로 두되 초기 규모에서 Sentinel로 운용해도 코드 변경 없이 승격이 기계적이다.
- Cluster 노드 접속 설정(`spring.data.redis.cluster.nodes` 등)·RTO/RPO·백업은 배포 오버레이와 §5 DR/HA 워크스트림이 소유한다(이 게이트 범위 밖).

## Lua keyslotting

- 회전 Lua(`rotate_session.lua`)가 만지는 모든 키가 단일 슬롯이어야 원자적이다 — Cluster는 CROSSSLOT 다중키 EVAL을 거부한다.
- `SessionKeys`가 키 스키마를 소유한다. 단일 슬롯 대상 키: `sess:{u:userId}:sid`(KEYS[1]), `sessidx:{u:userId}`(KEYS[2]), `grace:{u:userId}:jti`(ARGV gracePrefix로 SET), `sess:{u:userId}:sid`(ARGV sessPrefix로 REUSE DEL 루프). 전부 `{u:userId}` 태그라 동일 슬롯이다. `SessionKeysTest`가 `ClusterSlotHashUtil`로 이 동치를 강제한다.
- `refidx:<jtiHash>`는 `jti→userId|sessionId`를 resolve해야 해(치킨-에그) 태그를 담을 수 없다 → 의도적으로 슬롯 밖이며 **Lua 원자 경계에 넣지 않는다**. 회전은 refidx GET(사전) → Lua(원자) → refidx SET(후속)의 3단계이고, 각 refidx 접근은 단일 키 GET/SET이라 슬롯 무관하게 원자적이다.

## 지속성 (AOF)

- `appendonly yes` + `appendfsync everysec` — 노드 크래시 손실 창은 최대 1초. 세션 데이터는 재로그인으로 재생성 가능하다.
- 단, **revoke(DEL)는 재생성 불가**하다. revoke가 유실되면 무효화됐어야 할 세션이 부활한다 → 지속성보다 복제 정합(아래)이 revoke의 핵심 방어선이다. `appendfsync always`는 단일 노드 손실만 막고 복제 지연은 못 막는다.

## failover 시 revoke 유실 방지

- 위협: 비동기 복제 환경에서 마스터가 revoke(DEL)를 수행한 직후, 그 DEL이 복제되기 전에 마스터가 장애 나 replica가 승격되면 revoked 세션이 부활한다 → 제품 명제(실시간 강제 로그아웃)가 깨진다.
- 완화(창 축소): `min-replicas-to-write 1` + `min-replicas-max-lag 10`. in-sync replica가 없으면 쓰기를 거부해, 승격 후보가 lag 상한 내로 유지되게 한다. **이는 동기 복제(WAIT)가 아니다** — 특정 DEL이 승격 전 replica에 도달했다는 보장은 없고, lag 창(≤10초) 안의 write는 여전히 유실 가능하다. 즉 min-replicas는 유실 창을 **축소하지 제거하지 않는다**.
- 완전 차단(후속): 보안 크리티컬 revoke(재사용 감지 패밀리 전멸·관리자 강제 로그아웃)는 `WAIT numreplicas timeout`/`WAITAOF`로 복제 확인 후 반환해야 창이 닫힌다. 강제/원격 로그아웃 소비자가 등장하는 P3·이상탐지 잠금 P5에서 revoke 경로에 배선한다(현재 슬라이스는 정책 명문화까지).
- 비원자 multi-DEL도 유실 벡터다: `revokeAll`은 현재 세션별 N회 라운드트립 DEL이라 failover가 루프 중간에 끼면 일부 세션이 잔존한다. same-slot이라 단일 Lua로 원자화 가능하나, 원자화는 P3 강제 로그아웃 워크스트림의 후속으로 둔다(이 게이트는 외과적으로 fail-closed·keyslotting만 닫는다).

## fail-closed/open 정책 (코드가 강제)

판정 지점별로 페일 모드가 다르다. 두 경우 모두 보안 불변식(revoked·만료·미인가 세션을 절대 수락하지 않음, Redis 불가 시 토큰을 절대 발급하지 않음)을 지킨다 — 차이는 HTTP 시맨틱과 블라스트 반경이다.

- **읽기 핫패스 `validate`: fail-closed → `false` → 401.** 매 요청 O(1) 검증은 Redis 불가 시 무효로 처리해 서명이 유효한 토큰도 거부한다(가용성보다 실시간 무효화). 401 vs 503-for-reads 트레이드오프: 401을 택했다 — 읽기는 요청 100% 경로라 503로 바꾸면 failover 순간 그 슬롯 사용자 전원이 매 요청 503 폭풍을 맞고, 보안 불변식은 401과 동일하게 충족된다. 503-reads의 이점(access 토큰 미폐기)은 access TTL(15분)이 짧아 제한적이라 미채택.
- **쓰기·회전 `create`·`rotate`·`revoke`·`revokeAll`: fail-closed → 503(`SESSION_STORE_UNAVAILABLE`), 토큰 미발급.** 401이 아닌 이유: 유효한 리프레시 토큰을 무효로 오인해 재로그인을 강제하지 않기 위함이다. 503은 재시도 가능 신호이므로 전이 blip에 리프레시 토큰을 폐기하지 않는다.

블라스트 반경: 한 슬롯의 failover 수 초 동안 그 슬롯에 속한 사용자만 영향받는다(userId 샤딩). 전체 사용자 동시 영향이 아니다.

클라이언트 계약(소비 서비스가 지켜야 함):
- transient 401을 즉시 로그아웃/토큰 폐기로 해석하지 않는다 — 재시도 후에도 지속되면 로그아웃한다(failover 중 401이 대량 재로그인 폭주를 유발하지 않게).
- 503은 지수 백오프로 재시도한다(리프레시 토큰 보존).
- 실패한 logout은 서버가 보상하지 않는다 — logout이 503이면 세션은 살아남고 access는 TTL까지 유효하다. 클라이언트가 재시도하거나 access TTL 만료에 의존한다(재시도는 멱등: revoke는 이미 지운 키를 다시 지워도 무해).

## 알려진 창 (문서화된 위협)

- **회전 부분 성공 창**: `rotate`는 Lua로 회전을 원자 확정한 뒤 `refidx:<newJti>`를 별도 SET한다(슬롯 밖이라 Lua에 못 넣음). Lua ROTATED 확정 직후 refidx SET에서 Redis 예외가 나면, 회전은 이미 유효하므로 코드는 **503을 던지지 않고**(그러면 "완료된 회전"이 실패로 오보돼, 정상 사용자가 유예 밖 재제시 시 오탐 REUSE로 패밀리 전멸한다) rotated를 반환하고 refidx 실패는 `log.warn`한다. 귀결: 클라이언트는 새 토큰을 받되, 그 토큰의 refidx가 없어 **다음 회전에서 재로그인**한다. 미인가 접근은 새지 않는다(fail-closed). 이 창은 refidx가 슬롯 밖일 수밖에 없는 구조상 소멸하지 않으며, 드문 이벤트(정확히 그 순간의 Redis 예외)라 재로그인 강등으로 수용한다.
- **비원자 multi-key 쓰기**: `create`는 refidx를 마지막에 세워 부분 실패 시 세션이 resolve되지 않게 한다(fail-closed). 다만 중간 단계(sess 키 `expire` 이전) 실패 시 물리 TTL 없는 세션 해시가 접근 불가 상태로 잔류할 수 있다(비원자 multi-key의 내재적 잔류). `revokeAll`은 위 revoke 유실 절 참조.

## 타임아웃·운영설정

- `spring.data.redis.timeout: 250ms`(app-api) — **정상 연결의 명령 타임아웃 상한**이다. 멈춘 노드(부분 failover)에서 이미 확립된 연결의 명령이 무한 대기하지 않게 fail-closed 지연을 유계로 만든다.
- 한계: 이 값은 새 연결의 TCP stall(Lettuce `connectTimeout` 기본 10초)이나 재연결 큐잉(`disconnectedBehavior`)을 상한하지 않는다. 연결단 fail-closed 지연까지 유계로 하려면 Lettuce `TimeoutOptions`·`connectTimeout`·`disconnectedBehavior` 배선이 필요하다(후속 워크스트림).

## 범위 밖 / 후속

- WAIT/WAITAOF 기반 보안 크리티컬 revoke 동기 복제 확인 — P3(강제/원격 로그아웃)·P5(이상탐지 잠금).
- `revokeAll` 단일 Lua 원자화 — P3.
- Lettuce 연결단 타임아웃/재연결 정책 배선 — 운영 워크스트림.
- Cluster 노드 접속·RTO/RPO·백업/PITR·모니터링 — 배포·§5 DR/HA.
