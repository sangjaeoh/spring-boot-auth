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
include(":module-common:common-messaging")
include(":module-common:common-auth")
include(":module-common:common-web")
include(":module-domains:domain-auth")
include(":module-domains:domain-generic")
include(":module-domains:domain-user")
include(":module-infra:infra-crypto")
include(":module-infra:infra-messaging")
include(":module-infra:infra-redis")
include(":module-external:external-geoip")
include(":module-external:external-identity")
include(":module-external:external-notification")
include(":module-external:external-social")
include(":module-apps:app-api")

include(":module-apps:app-batch")
include(":module-apps:app-migration")
include(":architecture-tests")
