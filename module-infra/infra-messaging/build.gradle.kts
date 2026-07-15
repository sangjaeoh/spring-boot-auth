plugins {
    id("convention.infra-module")
}

// infra-messaging은 common-messaging 포트의 in-process transport(커밋 후 전달)와 멱등 소비 기반을 구현한다:
// msg 스키마(이 모듈 소유)의 processed_event 디둡 원장 + dead_letter_event DLQ + 백오프 재시도.
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-messaging"))
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    // 테스트가 msg 스키마 마이그레이션을 Boot Flyway(locations 단일 지정)로 실행한다.
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.spring.boot.flyway)
    testRuntimeOnly(libs.flyway.core)
    testRuntimeOnly(libs.flyway.database.postgresql)
}
