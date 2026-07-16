package com.example.auth.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 빌드가 강제하는 클래스 레벨 불변식(docs/architecture.md).
 *
 * <p>계층 의존 방향·금지 의존성은 컨벤션 플러그인이 컴파일 시점에 막으므로 여기서 다루지 않는다.
 * 소프트삭제 base finder 금지는 소프트삭제 엔티티가 등장하는 Phase 1에서 규칙을 추가한다.
 */
@AnalyzeClasses(packages = "com.example.auth", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule entities_reside_in_entity_packages = classes()
            .that()
            .areAnnotatedWith("jakarta.persistence.Entity")
            .should()
            .resideInAPackage("..entity..")
            .as("JPA 엔티티는 도메인 entity 패키지에만 둔다");

    // admin 격리 구역(infrastructure/query)만 엔티티·리포지토리 접근을 허용한다(docs/architecture.md —
    // 앱 모듈 구조·경계 원칙의 예외 격리 구역). batch reader 구역은 크로스 스키마 읽기가 생길 때 연다.
    private static final String ADMIN_QUERY_ZONE = "..app.admin.infrastructure.query..";

    @ArchTest
    static final ArchRule apps_do_not_depend_on_entities = noClasses()
            .that()
            .resideInAPackage("..app..")
            .and()
            .resideOutsideOfPackage(ADMIN_QUERY_ZONE)
            .should()
            .dependOnClassesThat()
            .areAnnotatedWith("jakarta.persistence.Entity")
            .as("앱은 엔티티에 의존하지 않는다(경계는 Info — admin 격리 구역 제외)");

    @ArchTest
    static final ArchRule apps_do_not_access_repositories = noClasses()
            .that()
            .resideInAPackage("..app..")
            .and()
            .resideOutsideOfPackage(ADMIN_QUERY_ZONE)
            .should()
            .dependOnClassesThat()
            .areAssignableTo("org.springframework.data.repository.Repository")
            .as("앱은 리포지토리에 직접 접근하지 않는다(admin 격리 구역 제외)");

    @ArchTest
    static final ArchRule module_base_packages_are_null_marked = classes()
            .that()
            .haveNameMatching(".*\\.package-info")
            .should()
            .beAnnotatedWith("org.jspecify.annotations.NullMarked")
            .as("모듈 베이스 패키지는 @NullMarked를 선언한다")
            .allowEmptyShould(false);
}
