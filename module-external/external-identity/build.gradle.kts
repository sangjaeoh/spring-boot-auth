plugins {
    id("convention.external-module")
}

// external-identity는 domain-user가 소유한 IdentityVerificationProvider 포트를 구현한다.
// dev/test는 Mock, 실 기관(NICE) 어댑터는 user.identity-verification.mode=nice로 전환한다
// (조달 미완 — 어댑터 골격 + 설정 스위치. 실 계약 페이로드 확정은 조달 완료 후 항목).
dependencies {
    implementation(project(":module-domains:domain-user"))
    implementation(libs.spring.context)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.web)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
