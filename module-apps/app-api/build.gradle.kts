plugins {
    id("convention.app-module")
}

// app-api는 공개 API·파사드·크로스도메인 조율을 소유한다(docs/architecture.md). 도메인·infra·common을 조립한다.
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-domains:domain-auth"))
    implementation(project(":module-infra:infra-crypto"))
    implementation(project(":module-infra:infra-redis"))
    implementation(project(":module-common:common-auth"))
    implementation(project(":module-common:common-web"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    // @EnableJpaRepositories·@EntityScan을 컴파일하려면 JPA가 컴파일 클래스패스에 필요하다(런타임은 domain-auth 전이).
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.spring.boot.starter.data.redis)

    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.restclient)
}
