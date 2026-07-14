// 금지 의존성을 차단한다(docs/code-quality.md — Lombok·H2 기각).
// Lombok: 생성 코드가 리뷰에 안 보임. H2: PostgreSQL과 방언·DDL divergence로 테스트가 거짓 신호가 됨.

configurations.configureEach {
    resolutionStrategy.eachDependency {
        val forbidden =
            (requested.group == "org.projectlombok" && requested.name == "lombok") ||
                (requested.group == "com.h2database" && requested.name == "h2")
        if (forbidden) {
            throw GradleException(
                "금지 의존성: ${requested.group}:${requested.name} — docs/code-quality.md(Lombok·H2 기각) 참조",
            )
        }
    }
}
