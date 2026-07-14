plugins {
    id("convention.domain-module")
}

// domain-auth는 인증 서비스 도메인(스키마 auth)을 소유한다: 인증계정·비밀번호·로그인이력 + 세션 영속 포트.
// (docs/architecture.md — 도메인은 common-core·common-jpa·common-messaging만 의존)
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
}
