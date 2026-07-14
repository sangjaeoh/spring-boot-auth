import org.gradle.api.artifacts.VersionCatalogsExtension

// infra 계층: 기술 구현체(Redis 등). 기본은 common만 의존(architecture.md 모듈 지도).
// 예외: infra-redis는 Redis 애그리거트(세션)의 도메인-소유 영속 포트를 Lua로 구현하므로 domain-auth에
// 의존한다 — IMPLEMENTATION_PLAN §1이 명시한 "Spring Data=포트가 성립하지 않는 유일 지점"의 인정 예외다.
// (architecture.md "infra→common" 불변식과 형식상 어긋나나 플랜의 명시 예외를 따른다. 물리 분리 시 재론.)

plugins {
    id("convention.java-base")
    id("convention.java-common")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", platform(libs.findLibrary("spring-boot-dependencies").get()))
    add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
}

// common은 항상 허용. domain-auth는 세션 스토어를 구현하는 infra-redis에만 허용한다.
val redisSessionException = name == "infra-redis"
enforceLayerDependencies { path ->
    path.startsWith(":module-common:") ||
        (redisSessionException && path == ":module-domains:domain-auth")
}
