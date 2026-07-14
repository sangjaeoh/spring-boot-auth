plugins {
    id("convention.common-module")
}

// common-jpa는 JPA 공통 지원(BaseTimeEntity·Auditing)을 소유한다(docs/architecture.md).
// SchemaFlywayFactory(멀티 스키마)는 두 번째 스키마가 등장하는 Phase 1에서 배선한다(증분).
dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.data.jpa)
}
