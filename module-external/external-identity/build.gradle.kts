plugins {
    id("convention.external-module")
}

// external-identity는 domain-user가 소유한 IdentityVerificationProvider 포트를 구현한다
// (dev/test는 Mock, 실 기관 NICE/KG/DANAL 어댑터는 P4).
dependencies {
    implementation(project(":module-domains:domain-user"))
    implementation(libs.spring.context)
}
