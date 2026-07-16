plugins {
    id("convention.app-module")
}

// app-admin은 어드민·백오피스 API(회원 검색/상세/이력·강제 로그아웃·잠금/해제·권한 변경·감사 조회)를
// 소유한다(docs/architecture.md — 앱 구성). 크로스 스키마 read는 infrastructure/query 격리 구역에만 둔다.
// 컴포넌트 스캔이 도메인 서비스의 포트 구현(infra·external)을 요구하므로 app-api와 같은 조립 폭을 갖는다.
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-jpa"))
    implementation(project(":module-common:common-messaging"))
    implementation(project(":module-common:common-auth"))
    implementation(project(":module-common:common-web"))
    implementation(project(":module-domains:domain-auth"))
    implementation(project(":module-domains:domain-generic"))
    implementation(project(":module-domains:domain-user"))
    implementation(project(":module-infra:infra-crypto"))
    implementation(project(":module-infra:infra-messaging"))
    implementation(project(":module-infra:infra-redis"))
    implementation(project(":module-external:external-geoip"))
    implementation(project(":module-external:external-identity"))
    implementation(project(":module-external:external-notification"))
    implementation(project(":module-external:external-social"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    // @EnableJpaRepositories·@EntityScan을 컴파일하려면 JPA가 컴파일 클래스패스에 필요하다(app-api와 동일).
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.spring.boot.starter.data.redis)

    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.restclient)
}
