# Spring Boot Agent Guide

Spring Boot + JPA 백엔드를 일관된 규율로 개발하기 위한 아키텍처·컨벤션·엔티티/영속·품질 가이드다.

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791)
![Gradle](https://img.shields.io/badge/Gradle-9.5-02303A)
![Architecture](https://img.shields.io/badge/Modular%20Monolith-MSA--ready-blue)

위 배지는 표시용이다. 버전 정본은 각 서비스의 `gradle/libs.versions.toml`이 소유한다.

이 저장소는 개발 규칙 문서만 담는다. 본 가이드의 규율은 아키텍처·컨벤션·엔티티/영속·품질도구를 다룬다.

## 기술 스택

이 가이드가 기본으로 전제하는 스택이다. 정확한 버전 핀은 [`code-quality`](docs/code-quality.md)와 각 서비스의 `gradle/libs.versions.toml`이 소유한다.

| 범주 | 채택 |
|---|---|
| 언어·런타임 | Java 25 |
| 프레임워크 | Spring Boot 4.1.x |
| 웹 | Spring MVC + 가상 스레드 |
| 영속 | Spring Data JPA · QueryDSL |
| DB | PostgreSQL 17 |
| 마이그레이션 | Flyway |
| 빌드 | Gradle 9.5.x (Kotlin DSL + 버전 카탈로그) |
| 아키텍처 | 멀티모듈 모듈러 모놀리식 |
| 포맷·정적분석 | Spotless (Palantir Java Format) · NullAway/JSpecify · Error Prone |
| 경계 강제 | 컨벤션 플러그인 · 아키텍처 테스트 |
| 테스트 DB | Testcontainers (PostgreSQL) |

## 문서

규칙은 `docs/`의 네 문서가 소유한다. 진입 앵커는 [`AGENTS.md`](AGENTS.md)다.

- [`docs/architecture.md`](docs/architecture.md) — 모듈 구조·패키지 구조·의존 방향·리포지토리 접근 범위.
- [`docs/coding-conventions.md`](docs/coding-conventions.md) — 타입 선언·객체 생성/변환·접근제한자·네이밍·주석.
- [`docs/entity-persistence.md`](docs/entity-persistence.md) — 엔티티 ID·버저닝·물리 FK 금지·연관·상태 전이.
- [`docs/code-quality.md`](docs/code-quality.md) — Spotless·NullAway·Error Prone 게이트와 도구 버전.

## 적용하기

- `docs/`와 진입 앵커(`AGENTS.md`·`CLAUDE.md`)를 서비스 레포로 복사한다.
- 이 가이드는 Gradle 멀티모듈 골격이 이미 구성된 저장소 위에서의 개발을 다스린다.
- 강제 장치는 `convention.java-base`·`java-common` 배선 → 계층 플러그인 → 아키텍처 테스트 순으로 채운다. 아직 배선되지 않은 불변식은 빌드가 막지 못하므로 리뷰로만 지켜진다(→ [architecture](docs/architecture.md)의 빌드가 강제하는 불변식).
