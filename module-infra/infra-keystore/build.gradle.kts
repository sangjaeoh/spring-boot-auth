plugins {
    id("convention.infra-module")
}

// infra-keystore는 JWT 서명키링(common-auth 소유 포트 SigningKeyStore)의 내구 공유 스토어를 구현한다:
// 공유 PostgreSQL의 keyring 스키마(이 모듈 소유)에 개인키 포함 JWK를 봉투암호화(EnvelopeCipher)로 영속해
// 재시작·다중 인스턴스(app-api·app-admin)에서 키·JWKS가 이어진다.
dependencies {
    implementation(project(":module-common:common-core"))
    implementation(project(":module-common:common-auth"))
    implementation(libs.spring.boot.starter.jdbc)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
    // IT가 키링-스토어 왕복(서명·검증)을 실키로 검증한다(Nimbus는 common-auth의 implementation 의존이라 미전이).
    testImplementation(libs.spring.security.oauth2.jose)
    // 테스트가 keyring 스키마 마이그레이션을 Boot Flyway(locations 단일 지정)로 실행한다.
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.spring.boot.flyway)
    testRuntimeOnly(libs.flyway.core)
    testRuntimeOnly(libs.flyway.database.postgresql)
}
