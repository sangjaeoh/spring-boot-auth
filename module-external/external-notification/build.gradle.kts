plugins {
    id("convention.external-module")
}

// external-notification은 알림 발송 포트 둘을 구현한다 — domain-auth의 VerificationCodeSender(OTP 원문
// 전달)와 domain-generic의 NotificationSender(카테고리 알림). dev/test는 Mock, 실 벤더 어댑터는
// generic.notification.mode=vendor로 전환한다(조달 미완 — 골격 + 설정 스위치).
dependencies {
    implementation(project(":module-domains:domain-auth"))
    implementation(project(":module-domains:domain-generic"))
    implementation(libs.spring.context)
    implementation(libs.spring.boot.autoconfigure)
    implementation(libs.spring.web)
    implementation(libs.slf4j.api)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
