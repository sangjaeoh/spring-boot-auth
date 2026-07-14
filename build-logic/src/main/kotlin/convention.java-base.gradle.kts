import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension

// 전 JVM 모듈 공통: 툴체인 + 포맷(Spotless/Palantir) + 정적분석(Error Prone + NullAway/JSpecify).
// (docs/code-quality.md — 세 도구는 이 플러그인이 일괄 적용한다)

plugins {
    java
    id("com.diffplug.spotless")
    id("net.ltgt.errorprone")
}

// 프리컴파일 스크립트 플러그인에서는 `libs` 접근자가 없으므로 런타임 카탈로그 API로 조회한다.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion.toInt())
    }
}

dependencies {
    "compileOnly"(libs.findLibrary("jspecify").get())
    "testCompileOnly"(libs.findLibrary("jspecify").get())
    "errorprone"(libs.findLibrary("errorprone-core").get())
    "errorprone"(libs.findLibrary("nullaway").get())
}

configure<SpotlessExtension> {
    java {
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        target("src/**/*.java")
        removeUnusedImports()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        excludedPaths.set(".*/build/generated/.*")
        check("NullAway", CheckSeverity.ERROR)
        // 루트 prefix로 전 모듈 서브트리를 검사 범위에 넣는다. 서브패키지 package-info 누락으로
        // 조용히 무검사되는 것을 막는다(docs/code-quality.md). @NullMarked는 모듈 베이스에 문서로 유지.
        option("NullAway:AnnotatedPackages", "com.example.auth")
        // JPA가 채우는 엔티티 필드는 NullAway 초기화 검사에서 제외한다(매핑 애노테이션 기준).
        // (docs/code-quality.md — NullAway:ExcludedFieldAnnotations / docs/entity-persistence.md)
        option(
            "NullAway:ExcludedFieldAnnotations",
            listOf(
                "jakarta.persistence.Id",
                "jakarta.persistence.EmbeddedId",
                "jakarta.persistence.Column",
                "jakarta.persistence.Enumerated",
                "jakarta.persistence.Convert",
                "jakarta.persistence.Embedded",
                "jakarta.persistence.ManyToOne",
                "jakarta.persistence.OneToOne",
                "jakarta.persistence.OneToMany",
                "jakarta.persistence.ManyToMany",
                "jakarta.persistence.JoinColumn",
                "jakarta.persistence.Version",
                "org.springframework.beans.factory.annotation.Autowired",
            )
                .joinToString(","),
        )
    }
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
