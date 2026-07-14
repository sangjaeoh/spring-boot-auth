plugins {
    id("convention.infra-module")
}

// infra-crypto는 common-core의 crypto 포트(PasswordHasher·TokenHasher)를 구현한다(common만 의존 — 정석 DIP).
// Argon2PasswordEncoder는 BouncyCastle을 런타임 필수로 쓰나 spring-security-crypto의 optional 의존이라 명시한다.
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(libs.spring.context)
    implementation(libs.spring.security.crypto)
    runtimeOnly(libs.bouncycastle.provider)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.bouncycastle.provider)
}
