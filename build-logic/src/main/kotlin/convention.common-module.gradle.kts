// common 계층: core 방향 단방향 의존만 허용, core는 의존 제로.
// (docs/architecture.md — 모듈 지도 / common 모듈 배치)

plugins {
    id("convention.java-base")
    id("convention.java-common")
}

// common 내부는 core 방향 단방향이다(architecture.md — core는 의존 제로, web→auth만 허용).
// 순서값이 더 낮은 common 모듈만 의존 가능: core(0) ← {jpa·messaging·auth}(1) ← web(2).
// 이로써 core 무의존·web→auth 허용·auth→web 금지가 컴파일 시점에 강제된다.
val order =
    mapOf(
        "common-core" to 0,
        "common-jpa" to 1,
        "common-messaging" to 1,
        "common-auth" to 1,
        "common-web" to 2,
    )
val selfOrder = order[name] ?: 1
enforceLayerDependencies { path ->
    val depOrder = order[path.substringAfterLast(':')]
    path.startsWith(":module-common:") && depOrder != null && depOrder < selfOrder
}
