import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

private val ENFORCED_CONFIGURATIONS = listOf("api", "implementation", "compileOnly", "runtimeOnly")

/**
 * 프로덕션 의존 구성에 등장하는 프로젝트 의존을 계층 규칙으로 검증한다.
 *
 * <p>계층 의존 방향(모듈 지도)은 빌드가 컴파일 시점에 강제한다(docs/architecture.md). 허용되지 않은
 * 프로젝트 의존이 선언되면 구성 평가 직후 빌드를 실패시킨다. 테스트 구성은 대상에서 제외한다.
 */
fun Project.enforceLayerDependencies(isAllowed: (String) -> Boolean) {
    val projectPath = path
    afterEvaluate {
        ENFORCED_CONFIGURATIONS.forEach { configurationName ->
            configurations
                .findByName(configurationName)
                ?.dependencies
                ?.filterIsInstance<ProjectDependency>()
                ?.forEach { dependency ->
                    if (!isAllowed(dependency.path)) {
                        throw GradleException(
                            "계층 의존 위반: $projectPath → ${dependency.path} (docs/architecture.md 모듈 지도)",
                        )
                    }
                }
        }
    }
}
