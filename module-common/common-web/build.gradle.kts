plugins {
    id("convention.common-module")
}

// common-web는 웹 공통(인증 필터·AuthUser·ProblemDetail 핸들러)을 소유한다(docs/architecture.md).
// 라우트별 SecurityFilterChain은 앱이 소유한다. 필터는 Spring Security 위에 선다.
dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-auth"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
