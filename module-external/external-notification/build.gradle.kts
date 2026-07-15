plugins {
    id("convention.external-module")
}

// external-notification은 domain-auth가 소유한 VerificationCodeSender 포트(OTP 원문 전달)를 구현한다.
// domain-generic의 NotificationSender(카테고리 알림) 구현은 발송 판정 슬라이스가 여기에 추가한다.
dependencies {
    implementation(project(":module-domains:domain-auth"))
    implementation(libs.spring.context)
    implementation(libs.slf4j.api)
}
