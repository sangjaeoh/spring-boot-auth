plugins {
    id("convention.app-module")
}

// Flyway 스키마별 독립 실행기(docs/architecture.md — 런타임 데이터 접근 없음, JDBC만).
// SchemaFlywayFactory(common-jpa)로 스키마마다 독립 history·독립 버전으로 마이그레이션한다.
// 도메인 모듈은 자기 스키마 마이그레이션 리소스를 제공하려 runtimeOnly로 싣는다(엔티티는 미사용 — JPA
// auto-config는 application.yml에서 exclude). Boot Flyway auto-config도 exclude(다중 스키마 버전 충돌 회피).
dependencies {
    implementation(project(":module-common:common-jpa"))

    runtimeOnly(project(":module-domains:domain-auth"))
    runtimeOnly(project(":module-domains:domain-generic"))
    runtimeOnly(project(":module-domains:domain-user"))
    // msg 스키마(디둡·DLQ)는 infra-messaging이 소유한다 — 마이그레이션 리소스 탑재용.
    runtimeOnly(project(":module-infra:infra-messaging"))

    implementation(libs.spring.boot.starter.jdbc)

    runtimeOnly(libs.spring.boot.flyway)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
}
