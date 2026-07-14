pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "spring-boot-auth"

// 모듈은 생성되는 대로 등록한다(module-{layer}/{prefix}-{name}).
include(":module-common:common-core")
include(":module-common:common-jpa")
include(":module-domains:domain-skeleton")
include(":module-apps:app-migration")
include(":architecture-tests")
