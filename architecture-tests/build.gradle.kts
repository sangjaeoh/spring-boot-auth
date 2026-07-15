plugins {
    id("convention.java-base")
    id("convention.java-common")
}

// 클래스 레벨 경계 불변식을 ArchUnit으로 강제한다(docs/architecture.md — 빌드가 강제하는 불변식).
// 계층 의존 방향은 컨벤션 플러그인이 컴파일 시점에 강제하므로 여기서 재검사하지 않는다.
dependencies {
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // ArchUnit이 임포트·검사할 프로덕션 클래스.
    testImplementation(project(":module-common:common-core"))
    testImplementation(project(":module-common:common-jpa"))
    testImplementation(project(":module-common:common-auth"))
    testImplementation(project(":module-common:common-web"))
    testImplementation(project(":module-domains:domain-auth"))
    testImplementation(project(":module-domains:domain-user"))
    testImplementation(project(":module-infra:infra-crypto"))
    testImplementation(project(":module-infra:infra-redis"))
    testImplementation(project(":module-external:external-notification"))
    testImplementation(project(":module-external:external-social"))
    testImplementation(project(":module-apps:app-api"))
    testImplementation(project(":module-apps:app-migration"))
}
