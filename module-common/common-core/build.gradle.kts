plugins {
    id("convention.common-module")
}

// common-core는 프레임워크 의존 제로의 순수 코드다(docs/architecture.md). 외부 의존을 두지 않는다.
// (JSpecify 애노테이션은 convention.java-base가 compileOnly로 제공한다)

// 테스트 의존은 테스트 클래스패스에만 실려 main 의존 제로를 유지한다.
dependencies {
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
