# Coding Conventions

## 언제

- 새 타입을 record·class·enum 중 무엇으로 선언할지 정할 때.
- 객체 생성 진입점·변환 방향을 정할 때.
- 클래스·메서드·필드의 접근제한자를 정할 때.
- 타입·메서드·필드 이름을 지을 때(패키지 이름·배치는 → [architecture](architecture.md)).
- 코드 주석·Javadoc을 쓸 때.

## 규칙

### 타입 선언

- 타입은 역할에 따라 아래 표대로 선언한다.

  | 대상 | 선언 | 근거 |
  |---|---|---|
  | JPA 엔티티 | class — 명시적 getter, `protected` 기본 생성자, setter 금지 | JPA 프록시·dirty checking, 불변식 보호 |
  | Info(경계 조회 모델) | record + `from(entity)` 정적 팩토리 | 불변 — 캐시 안전 |
  | 도메인 이벤트 | record | 불변 페이로드 |
  | request/response DTO | record (Bean Validation은 request에만) | 불변, 명시적 계약 |
  | 값 객체(Money·Email 등) | record | 동등성 = 값 |
  | ErrorCode | enum (`ErrorCode` 구현) | 도메인별 코드 집합 고정 |
  | 커스텀 예외 | class — 경계 도달 예외만 `BaseException` 상속(unchecked) | 스택트레이스·계층·`@Transactional` 기본 롤백 |
  | 서비스·파사드·설정 | class — `final` 필드 + 생성자 주입 | 상태 없는 협력자 |

- 커스텀 예외는 경계에 도달하는(핸들러까지 전파되는) 예외만 `BaseException`을 상속한다. 국소에서 잡아 처리하는 예외는 JDK 관용구(`IllegalArgumentException` 등)로 둔다.
  - 값 범위·불변식 위반은 유효한 외부 입력으로 도달할 수 있으면 `{Name}ErrorCode` 도메인 예외로, 외부 입력으로는 못 미치고 호출자 버그로만 깨지는 선행조건이면 `IllegalArgumentException`으로 둔다. 후자가 경계까지 전파되면 클라이언트 400이 아니라 서버 버그(500)다.
    - 같은 검사도 어느 계층이 막느냐로 버킷이 갈린다. 원생 외부 입력은 경계(요청 DTO 검증·타입된 입력)에서 먼저 걸러 도메인의 선행조건 검사가 명백한 버그 백스톱(IAE)이 되게 한다 — 그래야 새 호출 경로가 IAE를 클라이언트 유발 500으로 바꾸는 비국소 함정이 준다.
  - `BaseException`은 `ErrorCode`를 받는 생성자(`BaseException(ErrorCode)`) 하나를 노출한다. 커스텀 예외는 이를 그대로 전달한다(`super(errorCode)`).
  - 도메인마다 `{Name}ErrorCode` enum이 `common-core`의 `ErrorCode` 인터페이스를 구현한다. `ErrorCode`는 코드 문자열·메시지·HTTP 상태를 노출하고(`code()`·`message()`·`status()`), enum 상수가 이 값을 채운다. ProblemDetail 핸들러가 이 계약으로 응답을 만든다.

### 객체 생성·변환

- 엔티티 생성 진입점은 정적 팩토리 `create(...)` 하나다. public 생성자를 두지 않는다.
  - 불변식 검증은 팩토리가 하고, JPA 기본 생성자는 `protected`.
- 팩토리는 `create(...)`=새 엔티티, `from(source)`=단일 원본 변환(Info·Response), `of(...)`=값 조합(`Money.of(1000L)`)으로 네이밍한다.
- record 검증은 compact constructor에서 한다.
  - 위반은 생성 시점에 throw. 유효성 의심되는 record가 존재하지 않게.
- Info·DTO·VO의 컬렉션 필드는 생성 시 방어적 복사 + 불변화(`List.copyOf`)한다.
  - JPA가 관리하는 엔티티 매핑 컬렉션은 대상이 아니다 — 불변 리스트로 대체하면 Hibernate `PersistentCollection`의 변경 추적·`orphanRemoval`과 충돌한다.
- 객체 간 변환은 대상 타입의 정적 팩토리에 둔다. 별도 `*Mapper`·`*Converter` 매핑 클래스를 만들지 않는다.
  - 별도 Mapper(MapStruct 등)를 기각한 이유: 생성 코드가 리뷰에 안 보이는 마법이라 읽기·디버깅을 해친다.
  - JPA `AttributeConverter`(값↔컬럼 변환)는 예외다. 대상 VO·엔티티와 같은 도메인 모듈에 두고 `@Convert`로 적용한다.
  - 방향은 바깥이 안을 안다. Entity→Info는 도메인, Info→Response는 앱.
- 역방향 변환(`Info.toEntity()` 등)을 금지한다.
  - 조회 모델이 쓰기 경로로 역류하지 않게.

### 접근제한자

- 기본은 가장 좁게 둔다. 도메인 내부 전용 메서드·클래스는 package-private, public은 "경계 계약" 선언이다.
- 엔티티 반환 조회의 경계 노출 금지와 리포지토리 인터페이스 가시성 근거는 → [architecture](architecture.md)의 리포지토리 접근 범위가 소유한다.
- 필드는 전부 `private`(빈은 `private final`). `protected` 필드를 두지 않는다.
- 로거는 `private static final`로 선언한다. `System.out`·`printStackTrace`를 쓰지 않는다.
- 테스트를 위해 접근을 완화하지 않는다.
  - 테스트가 안 되면 설계를 바꾸지 가시성을 바꾸지 않는다.

### 네이밍

- 엔티티는 단수 명사(`Order`), `@Table`명은 snake_case 단수(`order_line`).
  - 예약어 회피 등 표준 divergence(`User` 엔티티 → `users` 테이블)는 각 서비스의 도메인 용어집이 등재해 코드마다 갈리지 않게 한다.
  - 용어집이 없으면 divergence를 임의로 만들지 말고 사람 확인을 요청한다.
- 서비스는 아래 고정 접미사만 쓴다. 새 접미사는 사람 승인이 필요하다.

  | 접미사 | 역할 |
  |---|---|
  | `Reader` | 조회 |
  | `Appender` | 생성 |
  | `Modifier` | 수정 |
  | `Remover` | 삭제 |
  | `Processor` | 복합·상태 전이 |
  | `Validator` | 검증 |

- `Modifier`와 `Processor`의 판정 기준은 → [entity-persistence](entity-persistence.md)가 소유한다.
- 조회·생성·업데이트 메서드는 계층별로 아래 표대로 이름 짓는다.

  | 작업 | 리포지토리 | 도메인 서비스(Reader·Appender·Modifier) | 엔티티 |
  |---|---|---|---|
  | 조회 | `find*`=`Optional`, `get*`=없으면 `*NotFoundException`, `exists*`·`count*`; 파생은 `findBy{조건}` | `get*`·`find*` → `Info` 반환 | — |
  | 생성 | Spring Data `save` | 도메인 의도 동사(`register`·`place`·`issue`) → 새 ID 반환 | 정적 팩토리 `create(...)` |
  | 업데이트 | dirty checking(명시 `save` 금지) | 도메인 의도 동사(`cancel`·`rename`·`relocate`) → 최소 결과 | 의도 동사 메서드(`activate()`·`delete()`) |

- 소프트삭제 엔티티 조회는 삭제 미포함이 기본이고, 삭제 포함 조회만 이름을 달리한다(정확한 활성 필터·삭제 포함 네이밍 규칙은 → [architecture](architecture.md)의 리포지토리 접근 범위).
- boolean 반환 메서드는 `is*`·`has*`·`can*`로 짓는다.
- 범용 CRUD 동사(`update`·`set`·무의미한 `change`)는 의도를 숨긴다. 도메인 의도 동사를 쓴다.
- 하드삭제(물리 DELETE)는 드물고 리뷰 게이트다. 명시 동사(`purge*`·`anonymize*`)로만 연다. 소프트삭제 `delete()`는 → [entity-persistence](entity-persistence.md)가 소유한다.
- 엔티티 `create`/`from`/`of` 팩토리 네이밍은 위 객체 생성·변환 규칙이, 상태 전이 의도 메서드는 → [entity-persistence](entity-persistence.md)가 소유한다(표는 참조).
- 리포지토리 조회의 파생/`@Query`/QueryDSL 선택은 → [architecture](architecture.md)의 리포지토리 접근 범위가 소유한다(표의 리포지토리 열은 이름만).
- 클래스는 아래 고정표 접미사만 쓴다.

  | 접미사 | 대상 |
  |---|---|
  | `Controller` | 컨트롤러 |
  | `Facade` | 파사드 |
  | `Repository` | 리포지토리 |
  | `Info` | 경계 조회 모델 |
  | `Request` / `Response` | DTO |
  | (과거형, 접미사 없음) | 도메인 이벤트 |
  | `ErrorCode` | 에러 코드 enum |
  | `Exception` | 커스텀 예외 |
  | `Config` | 설정 |
  | `Aspect` | AOP |
  | `Converter` | JPA `AttributeConverter` |
  | `Fixture` | 테스트 픽스처 |

- 표에 없는 접미사(`Manager`·`Util`·`Helper`·`Service`)는 도입 전 의심한다.
  - 대부분 역할이 정의되지 않았다는 증거다.

### 코드 주석·Javadoc

- Javadoc은 API 계약이다. 공개(`public`·`protected`) 타입·멤버에 호출자가 알아야 할 계약(무엇을 보장하나)을 적고 구현 방식(How)은 적지 않는다.
  - 첫 문장은 3인칭 서술형 요약(`사용자를 등록한다`), 마침표로 끝낸다.
  - 명령형(`~하라`)·장황한 주어형(`이 메서드는 …을 반환한다`)은 피한다. 자명한 접근자·오버라이드는 생략한다.
  - 주석은 문서화 대상 자신의 계약만 적는다. 협력자를 거쳐 실현되더라도 대상의 관측 가능한 계약(전파 예외·외부 효과)은 적되, 협력자의 내부 동작이나 다른 요소의 계약은 되풀이하지 않는다.
  - Javadoc 구조(요약·태그 유효성)는 빌드가 강제한다 → [code-quality](code-quality.md)
- 구현 주석은 비자명한 "왜"만 적는다. 코드가 이미 말하는 것(What)을 되풀이하지 않는다.
  - 시점 없는 사실로 쓴다("우리가 …를 택했다"가 아니라 "…이므로 …한다").
  - 어려운 블록은 주석보다 리팩터·이름 개선을 먼저 한다.
- 표기는 코드·값은 `{@code …}`, 타입은 `{@link …}`.
- 주석에 넣지 않는 것:
  - 결정 내러티브 금지. 무엇을 왜 채택/폐기했는지는 문서에 적는다.
  - 검사가 지키는 것 되풀이 금지. 컴파일·포맷·경계 게이트가 잡는 걸 "지우지 마시오"류로 반복하지 않는다.
  - 주석 처리된 코드 금지(git 이력이 보존한다).
  - 작성자·날짜 금지(git이 추적한다).
  - TODO엔 이슈 번호를 단다(`// TODO(#123): …`).
