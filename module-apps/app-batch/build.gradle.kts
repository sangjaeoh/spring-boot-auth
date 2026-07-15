plugins {
    id("convention.app-module")
}

// app-batch는 배치·정리 잡(휴면 전환·사전통지·보존 파기)을 소유한다(docs/architecture.md — 앱 구성).
// 잡 로직은 도메인 서비스가 소유하고(데이터 소유=파기 소유) 배치는 조율·구동만 한다 — 엔티티 격리
// 구역(infrastructure/reader)은 크로스 스키마 읽기가 생길 때 연다. 컴포넌트 스캔이 도메인 서비스의
// 포트 구현(infra·external)을 요구하므로 app-api와 같은 조립 폭을 갖되 웹·보안은 싣지 않는다.
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    implementation(project(":module-common:common-messaging"))
    implementation(project(":module-domains:domain-auth"))
    implementation(project(":module-domains:domain-generic"))
    implementation(project(":module-domains:domain-user"))
    implementation(project(":module-infra:infra-crypto"))
    implementation(project(":module-infra:infra-messaging"))
    implementation(project(":module-infra:infra-redis"))
    implementation(project(":module-external:external-identity"))
    implementation(project(":module-external:external-notification"))
    implementation(project(":module-external:external-social"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
}
