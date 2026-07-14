import org.gradle.api.artifacts.VersionCatalogsExtension

// app 계층: Spring Boot 실행 조립. domains·infra·external·common 전부 의존 허용(계층 강제 없음).
// (docs/architecture.md — 모듈 지도 / 앱 구성)

plugins {
    id("convention.java-base")
    id("convention.java-common")
    id("org.springframework.boot")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", platform(libs.findLibrary("spring-boot-dependencies").get()))
}

addSpringIntegrationTestInfra(libs)
