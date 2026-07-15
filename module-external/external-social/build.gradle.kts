plugins {
    id("convention.external-module")
}

// external-social은 domain-auth가 소유한 SocialIdentityProvider 포트를 구현한다.
// dev/test는 Mock, 실 4사(카카오·네이버·구글·애플) OIDC 어댑터는 auth.social.mode=oidc로 전환한다
// (id_token 검증은 Nimbus JOSE 위탁 — 자작 금지 결정).
dependencies {
    implementation(project(":module-domains:domain-auth"))
    implementation(libs.spring.context)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.security.oauth2.jose)
    implementation(libs.slf4j.api)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
