plugins {
    id("convention.app-module")
}

// app-api는 공개 API·파사드·크로스도메인 조율을 소유한다(docs/architecture.md). 도메인·infra·common을 조립한다.
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    implementation(project(":module-common:common-messaging"))
    implementation(project(":module-domains:domain-auth"))
    implementation(project(":module-domains:domain-generic"))
    implementation(project(":module-domains:domain-user"))
    implementation(project(":module-infra:infra-crypto"))
    implementation(project(":module-infra:infra-keystore"))
    implementation(project(":module-infra:infra-messaging"))
    implementation(project(":module-infra:infra-redis"))
    implementation(project(":module-external:external-geoip"))
    implementation(project(":module-external:external-identity"))
    implementation(project(":module-external:external-notification"))
    implementation(project(":module-external:external-social"))
    implementation(project(":module-common:common-auth"))
    implementation(project(":module-common:common-web"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    // 관측성: health probe + Prometheus 메트릭(관리 포트 분리 — application.yml management 블록).
    implementation(libs.spring.boot.starter.actuator)
    runtimeOnly(libs.micrometer.registry.prometheus)
    // OpenAPI 계약 게시(/v3/api-docs — 계약 검증은 OpenApiContractE2EIT).
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    // @EnableJpaRepositories·@EntityScan을 컴파일하려면 JPA가 컴파일 클래스패스에 필요하다(런타임은 domain-auth 전이).
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.spring.boot.starter.data.redis)

    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.restclient)
    // 온보딩 E2E가 세션 hash의 PII 원문 부재를 Redis에서 직접 검증한다(런타임은 runtimeOnly로 이미 존재).
    testImplementation(libs.spring.boot.starter.data.redis)
    // JWKS 왕복검증 E2E: 게시된 공개키로 실제 발급 토큰을 디코드해 계약을 증명한다(Nimbus는 common-auth의
    // implementation 의존이라 app-api 테스트 컴파일 클래스패스에 전이되지 않으므로 명시).
    testImplementation(libs.spring.security.oauth2.jose)
}

tasks.test {
    // 부하 프로파일 옵트인 전달 — 기본 빌드는 LoadProfileIT를 스킵한다(docs/ops/load-report.md).
    systemProperty("loadProfile", System.getProperty("loadProfile", "false"))
}
