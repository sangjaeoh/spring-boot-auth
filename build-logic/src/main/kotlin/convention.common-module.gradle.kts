// common 계층: core 방향 단방향 의존만 허용, core는 의존 제로.
// (docs/architecture.md — 모듈 지도 / common 모듈 배치)

plugins {
    id("convention.java-base")
    id("convention.java-common")
}

// core는 프로젝트 의존 제로. 그 외 common은 common 계층 안에서만 의존한다.
// web→auth 같은 common 내부 세부 방향은 해당 모듈이 등장할 때 좁힌다.
val isCore = name == "common-core"
enforceLayerDependencies { path -> !isCore && path.startsWith(":module-common:") }
