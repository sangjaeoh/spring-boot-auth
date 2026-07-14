import org.gradle.api.artifacts.VersionCatalogsExtension

// domain 계층: JPA + 허용 의존 화이트리스트(common-core·jpa·messaging).
// QueryDSL apt는 첫 프래그먼트가 등장하는 단계에서 배선한다(증분).
// (docs/architecture.md — 모듈 지도 / 컨벤션 플러그인)

plugins {
    id("convention.java-base")
    id("convention.java-common")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", platform(libs.findLibrary("spring-boot-dependencies").get()))
    add("implementation", libs.findLibrary("spring-boot-starter-data-jpa").get())

    // 도메인은 자기 스키마 마이그레이션을 소유한다. Flyway·드라이버는 테스트(및 app-migration)에서 실행된다.
    // spring-boot-flyway = Spring Boot 4의 Flyway auto-config 모듈(flyway-core만으론 마이그레이션 미실행).
    add("runtimeOnly", libs.findLibrary("postgresql").get())
    add("runtimeOnly", libs.findLibrary("spring-boot-flyway").get())
    add("runtimeOnly", libs.findLibrary("flyway-core").get())
    add("runtimeOnly", libs.findLibrary("flyway-database-postgresql").get())
}

addSpringIntegrationTestInfra(libs)

enforceLayerDependencies { path ->
    path in
        setOf(
            ":module-common:common-core",
            ":module-common:common-jpa",
            ":module-common:common-messaging",
        )
}
