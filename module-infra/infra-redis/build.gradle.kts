plugins {
    id("convention.infra-module")
}

// infra-redis는 세션(AccountSessions)의 도메인-소유 영속 포트를 Lua 원자성으로 구현한다.
// domain-auth 의존은 convention.infra-module이 이 모듈에만 허용하는 Redis 애그리거트 예외다(플러그인 주석).
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-domains:domain-auth"))
    implementation(libs.spring.boot.starter.data.redis)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit)
}
