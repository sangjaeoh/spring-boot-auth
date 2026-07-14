# Code Quality

## 언제

- 포맷·null·정적분석 게이트가 무엇을 강제하는지 확인할 때.
- 도구 버전을 정하거나 올릴 때.
- 새 모듈에 품질 게이트를 배선할 때.

## 규칙

- 이 문서는 포맷·null·정적분석 세 도구만 다룬다. 계층 의존·엔티티 비노출 등 경계 강제는 컨벤션 플러그인·아키텍처 테스트가 담당하며, 강제 대상 불변식은 → [architecture](architecture.md)의 빌드가 강제하는 불변식이 소유한다.
- 세 도구(Spotless·NullAway·Error Prone)는 `convention.java-base` 컨벤션 플러그인이 전 JVM 모듈에 일괄 적용한다(플러그인 체계는 → [architecture](architecture.md)).
- `convention.java-common`이 금지 의존성(Lombok·H2)을 차단한다.
  - Lombok을 기각한 이유: 애노테이션 생성 코드가 리뷰에 안 보이고 디버깅·바이트코드 도구와 마찰. record + 명시 코드를 쓴다.
  - H2를 기각한 이유: 방언·DDL·`ddl-auto=validate`·Flyway·`NO_CONSTRAINT` FK가 PostgreSQL과 divergence해 통과가 거짓 신호가 된다. 영속 테스트는 실 PostgreSQL(Testcontainers)에 대해 돌린다.

### Spotless (Palantir Java Format)

- `spotlessCheck`가 빌드 게이트다. 어긋난 포맷은 `./gradlew spotlessApply`가 자동 교정한다. 전체 게이트는 `./gradlew build`가 돌린다.
- 수동 스타일 문서를 두지 않는다.
  - 사람이 지키는 스타일은 리뷰 소음이 되고 결국 어긋나므로, 포맷·스타일 논쟁을 자동 교정으로 없앤 것을 택했다. `spotlessCheck`가 게이트라 포맷 규칙을 프로즈로 두지 않는다.

### NullAway + JSpecify

- NullAway는 `@NullMarked` 범위(각 모듈 베이스 패키지에 배치 → [architecture](architecture.md))에서 미주석 참조를 non-null로 보고, null 계약 위반을 빌드에서 잡는다. nullable 값은 `@Nullable`로 표기하면 게이트가 그 참조의 null 처리를 요구한다.
  - 전량 조건: `@NullMarked`가 적용된 범위에서만 검사하므로, 한 모듈이라도 베이스 패키지에서 `@NullMarked`가 빠지면 그 모듈은 조용히 무검사가 된다.
- JPA가 채우는 엔티티 필드는 초기화 검사에서 제외한다. `convention.java-base`의 `NullAway:ExcludedFieldAnnotations`에 JPA 매핑 애노테이션(`jakarta.persistence`의 `Id`·`Column`·`Enumerated`·`Convert`·`Embedded`·`ManyToOne`·`OneToOne`·`OneToMany`·`JoinColumn`·`Version` 등)을 등록한다.
  - `@NullMarked`가 켜지면 NullAway는 `protected` 무인자 생성자가 초기화하지 않는 non-null 필드를 전부 위반으로 본다. 매핑 애노테이션이 붙은 필드는 이 배선이 일괄 제외하므로 클래스 단위 `@SuppressWarnings("NullAway.Init")`가 필요 없다.
  - 애노테이션 기반 제외는 무애노테이션 필드를 못 덮는다. 모든 영속 non-null 필드에 매핑 애노테이션을 명시하고 nullable 값만 `@Nullable`로 남기는 규칙이 이를 보장한다 → [entity-persistence](entity-persistence.md).

### 정적분석 억제

- 정적분석(NullAway·Error Prone) 위반은 억제가 아니라 설계 변경으로 없앤다.
  - `@SuppressWarnings`는 예외다. 필드·메서드 최소 스코프로 좁히고 비자명한 이유 주석을 단다. 위반을 억제로 도망치면 게이트가 무력화된다.

### Error Prone

- Error Prone이 버그 패턴과 Javadoc 구조(요약·태그 유효성)를 컴파일 시점에 강제한다.
  - Javadoc 산문 규약(3인칭 요약 등)은 → [coding-conventions](coding-conventions.md)

### 버전

- 버전 정본은 각 서비스의 `gradle/libs.versions.toml`이다. 아래 표는 편의 사본이고, 불일치 시 toml이 이긴다.
- 의존성은 빌드스크립트에서 카탈로그 별칭(`libs.…`)으로만 선언한다. 버전 리터럴을 두지 않는다.
  - 버전이 스크립트에 흩어지면 정본(toml)과 드리프트한다.

  | 항목 | 권장 baseline |
  |---|---|
  | Java | 25 (LTS) |
  | Spotless (Palantir Java Format) | 8.8.x (Palantir 2.94.x) |
  | JSpecify | 1.0.x |
  | NullAway | 0.13.x |
  | Error Prone | 2.50.x |
