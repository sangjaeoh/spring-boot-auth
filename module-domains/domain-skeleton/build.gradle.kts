plugins {
    id("convention.domain-module")
}

// Phase 0 walking skeleton. 마이그레이션→엔티티→리포지토리 파이프라인을 증명하는 임시 도메인이다.
// Phase 1a 착수 시 실제 인증 애그리거트로 대체·제거한다.
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
}
