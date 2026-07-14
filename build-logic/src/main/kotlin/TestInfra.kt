import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog

/**
 * Spring Boot + Testcontainers(PostgreSQL) 통합 테스트 공통 의존을 배선한다.
 *
 * <p>domain·app 모듈이 공유한다. Testcontainers 2.0 좌표(testcontainers-*)와 Gradle 9가 요구하는
 * JUnit Platform launcher를 포함한다.
 */
fun Project.addSpringIntegrationTestInfra(libs: VersionCatalog) {
    dependencies.add("testImplementation", libs.findLibrary("spring-boot-starter-test").get())
    dependencies.add("testImplementation", libs.findLibrary("spring-boot-testcontainers").get())
    dependencies.add("testImplementation", libs.findLibrary("testcontainers-postgresql").get())
    dependencies.add("testImplementation", libs.findLibrary("testcontainers-junit").get())
    dependencies.add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
}
