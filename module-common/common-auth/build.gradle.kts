plugins {
    id("convention.common-module")
}

// common-auth는 JWT(Access) 발급/검증 원자재를 소유한다(docs/architecture.md). 웹 필터는 common-web.
// D7: JWT는 Nimbus JOSE에 위탁 — spring-security-oauth2-jose의 NimbusJwtEncoder/Decoder를 쓴다.
dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":module-common:common-core"))
    implementation(libs.spring.security.oauth2.jose)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
