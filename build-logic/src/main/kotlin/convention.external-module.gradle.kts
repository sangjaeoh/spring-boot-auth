import org.gradle.api.artifacts.VersionCatalogsExtension

// external 계층: 외부 시스템 어댑터(알림·PG 등). 구현 대상 domain + common만 의존(architecture.md 모듈 지도).
// (docs/architecture.md — 모듈 지도 / infra vs external 판정)

plugins {
    id("convention.java-base")
    id("convention.java-common")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", platform(libs.findLibrary("spring-boot-dependencies").get()))
}

// external은 구현 대상 domain과 common만 의존한다. 구체 domain은 모듈별로 다르므로 domains 전역을 허용한다.
enforceLayerDependencies { path ->
    path.startsWith(":module-common:") || path.startsWith(":module-domains:")
}
