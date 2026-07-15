plugins {
    id("convention.common-module")
}

// common-jpa는 JPA 공통 지원(BaseTimeEntity·Auditing·SchemaFlywayFactory·PII 컬럼 컨버터)을 소유한다.
// (docs/architecture.md — common 모듈 배치) common-core 방향 단방향(crypto 포트 소비).
dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.data.jpa)
    // PII 컨버터가 소비하는 crypto 포트(EnvelopeCipher). core 방향 단방향(0<1).
    implementation(project(":module-common:common-core"))
    // SchemaFlywayFactory가 스키마별 Flyway 인스턴스를 조립한다(2번째 스키마 usr 등장 → 이연 해제).
    implementation(libs.flyway.core)
}
