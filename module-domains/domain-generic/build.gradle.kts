plugins {
    id("convention.domain-module")
}

// domain-generic은 제네릭 서비스 도메인(스키마 generic)을 소유한다: 알림(Notification) 발송 이력·발송 판정.
// (docs/architecture.md — 도메인은 common-core·common-jpa·common-messaging만 의존)
//
// NotificationSender 포트 소유: DOMAIN_MODEL §3.1대로 카테고리 알림 포트(EMAIL/SMS/PUSH)는 이 모듈이
// 소유한다. domain-auth의 기존 포트는 OTP 원문 전달 전용 계약이라 VerificationCodeSender로 개명해 남긴다 —
// 도메인 간 의존이 금지라 포트를 공유할 수 없고(각 소비 도메인이 자기 포트를 소유, docs/architecture.md
// 경계 원칙), OTP 발송은 수신설정 판정·발송 이력 없이 주어진 target으로 즉시 전달하는 별개 계약이다.
// external-notification이 두 포트를 함께 구현한다.
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
}
