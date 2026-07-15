plugins {
    id("convention.domain-module")
}

// domain-user는 유저 서비스 도메인(스키마 usr)을 소유한다: 회원(User) + PII 암호화·전화 blind index.
// (docs/architecture.md — 도메인은 common-core·common-jpa·common-messaging만 의존) crypto 포트는 common-core,
// PII 컨버터는 common-jpa 소유이며 infra-crypto(구현)에는 의존하지 않는다(계층).
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
}
