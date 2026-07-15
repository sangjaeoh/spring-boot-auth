plugins {
    id("convention.external-module")
}

// external-notification은 domain-auth가 소유한 NotificationSender 포트를 구현한다(dev/test는 Mock, 실 발송은 P5).
dependencies {
    implementation(project(":module-domains:domain-auth"))
    implementation(libs.spring.context)
    implementation(libs.slf4j.api)
}
