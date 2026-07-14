plugins {
    id("convention.app-module")
}

// Flyway 스키마별 독립 실행기(docs/architecture.md — 런타임 데이터 접근 없음, JDBC만).
// Phase 0: 실행기 스캐폴드 + Flyway/DataSource 배선 검증. 도메인 스키마 마이그레이션 집약과
// SchemaFlywayFactory(스키마별 인스턴스)는 두 번째 스키마가 등장하는 Phase 1에서 배선한다.
dependencies {
    implementation(libs.spring.boot.starter.jdbc)

    runtimeOnly(libs.spring.boot.flyway)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
}
